package br.com.redesurftank.havalshisuku.projectors

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.MainUiManager
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.SteeringWheelAcControlType
import br.com.redesurftank.havalshisuku.models.screens.GraphicsScreen
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import br.com.redesurftank.havalshisuku.models.screens.MainMenu
import br.com.redesurftank.havalshisuku.models.screens.RegenScreen
import br.com.redesurftank.havalshisuku.models.screens.Screen
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.get
import kotlinx.coroutines.*

class InstrumentProjector2(private val outerContext: Context, display: Display) :
        BaseProjector(outerContext, display) {

    private val TAG = "InstrumentProjector2"
    private val DEBUG_EXTERNAL_APP_HTML = "/data/local/tmp/app.html"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val clockRunnable =
            object : Runnable {
                override fun run() {
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    evaluateJsIfReady(webView, "control('clockTime', '$time')")
                    handler.postDelayed(this, 30000) // Update every 30s
                }
            }
    private val preferences: SharedPreferences =
            App.getDeviceProtectedContext()
                    .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    private var webView: WebView? = null
    private val webViewsLoaded = mutableMapOf<WebView, Boolean>()
    private val pendingJsQueues = mutableMapOf<WebView, MutableList<String>>()
    private lateinit var root: FrameLayout

    // Cached EV power values for kW calculation
    private var batteryVoltage = 0f
    private var batteryCurrent = 0f
    private var isAnyAppOnDisplay3 = false
    private var isAnyAppOnDisplay1 = false
    private var currentCard = 0
    private var isWarningActive = false
    private var hasAutoLaunched = false
    private val lastAppliedConfigs =
            mutableMapOf<String, br.com.redesurftank.havalshisuku.models.DisplayAppConfig>()

    private var currentMediaController: MediaController? = null
    private var mediaCallback: MediaController.Callback? = null
    private var mediaSessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var radioFavorites: List<Long> = emptyList()  // kHz, loaded from ITS
    private var currentFreqKhz: Long = 0L
    private var isRadioPlaying: Boolean = false
    private var isRadioSeeking: Boolean = false
    private var lastNavTime: Long = 0L
    private var lastAaMediaId: String = ""
    private var isRadioSource: Boolean = false  // true when FM/AM is active audio source

    private var cachedAaTitle: String = ""
    private var cachedAaArtist: String = ""
    private var aaLogcatJob: Job? = null
    private val aaPollRunnable = object : Runnable {
        override fun run() {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) { pollAndroidAutoTrack() }
            handler.postDelayed(this, 2000)
        }
    }

    private var lastHeartbeatTime = System.currentTimeMillis()
    private val watchdogRunnable =
            object : Runnable {
                override fun run() {
                    val now = System.currentTimeMillis()
                    // If no heartbeat for 15 seconds, and the projector should be visible, reload
                    if (now - lastHeartbeatTime > 15000 && shouldShowProjector() && root.isVisible
                    ) {
                        Log.e(
                                TAG,
                                "WebView watchdog triggered: No heartbeat for ${now - lastHeartbeatTime}ms. Reloading..."
                        )
                        ensureUi { webView?.reload() }
                        lastHeartbeatTime =
                                System.currentTimeMillis() // Reset to avoid immediate re-trigger
                    }
                    handler.postDelayed(this, 5000) // Check every 5s
                }
            }

    val monitoredWarningKeys =
            setOf(
                    CarConstants.CAR_BASIC_COOLANT_TEMP_WARNING.value,
                    CarConstants.CAR_BASIC_ENGINE_OIL_LOW_PRESSURE_WARNING.value,
                    CarConstants.CAR_BASIC_FATIGUE_WARNING.value,
                    // CarConstants.CAR_BASIC_MAINTENANCE_WARNING.value,
                    CarConstants.CAR_BASIC_OIL_LOW_WARNING.value,
                    CarConstants.CAR_BASIC_SEAT_BELT_WARNING.value,
                    CarConstants.CAR_BASIC_TIREPRESS_WARNING.value,
                    CarConstants.CAR_BASIC_TIRETEMP_WARNING.value,
                    CarConstants.CAR_BASIC_TPMS_WARNING.value,
                    CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT.value,
                    CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT.value,
                    // CarConstants.CAR_IPK_INFO_DOW_WARNING_REQLEFT.value,
                    // CarConstants.CAR_IPK_INFO_DOW_WARNING_REQRIGHT.value,
                    // CarConstants.CAR_IPK_INFO_FCTA_WARNING.value,
                    // CarConstants.CAR_IPK_INFO_FCW_WARNING.value,
                    CarConstants.CAR_IPK_INFO_WARNING_TTS_NOTIFY.value,
                    CarConstants.CAR_IPK_LIGHT_DOOR_WARNING.value,
                    CarConstants.CAR_IPK_LIGHT_ENGINE_OIL_LOW_PRESSURE_WARNING.value,
                    // CarConstants.CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR.value,
                    CarConstants.CAR_IPK_LIGHT_TPMS_WARNING.value,
                    CarConstants.CAR_IPK_LIGHT_FUEL_LOW
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
                                        SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key,
                                        SharedPreferencesKeys.CLUSTER_FUEL_DISPLAY_UNIT.key,
                                        SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_ACTIVE.key,
                                        SharedPreferencesKeys.TRIP_CONSISTENCY_CLUSTER_SCORE.key,
                                        SharedPreferencesKeys.CLUSTER_MEDIA_BAR_ENABLED.key,
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
                        if (key == SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key ||
                                        key == SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key
                        ) {
                            Log.d(TAG, "Theme changed, reloading WebView")
                            webView?.loadDataWithBaseURL(
                                    getThemeBaseUrl(),
                                    readAppContent(outerContext),
                                    "text/html",
                                    "UTF-8",
                                    null
                            )
                        }
                        if (key == SharedPreferencesKeys.CLUSTER_FUEL_DISPLAY_UNIT.key) {
                            val unit = getClusterFuelDisplayUnit()
                            Log.d(TAG, "[HavalDev] Cluster fuel display unit changed: $unit")
                            evaluateJsIfReady(webView, "control('fuelDisplayUnit', '$unit')")
                        }
                        if (key == SharedPreferencesKeys.CLUSTER_MEDIA_BAR_ENABLED.key) {
                            val enabled = preferences.getBoolean(key, true)
                            evaluateJsIfReady(webView, "control('mediaBarEnabled', $enabled)")
                            if (enabled) {
                                if (isRadioPlaying && currentFreqKhz > 0) {
                                    val band = if (currentFreqKhz >= 76000) "FM" else "AM"
                                    val title = "${"%.1f".format(currentFreqKhz / 1000.0)} $band"
                                    evaluateJsIfReady(webView, "control('mediaSource', '$band')")
                                    evaluateJsIfReady(webView, "control('mediaTitle', '${title.escapeForJs()}')")
                                    evaluateJsIfReady(webView, "control('mediaArtist', '')")
                                    evaluateJsIfReady(webView, "control('mediaIsPlaying', true)")
                                } else {
                                    scope.launch(Dispatchers.IO) { pollAndroidAutoTrack() }
                                }
                            }
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
                                shouldShowProjector() && ServiceManager.getInstance().isMainScreenOn
                        updateVirtualClusterVisibility()
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

    private val eventListener =
            br.com.redesurftank.havalshisuku.listeners.IServiceManagerEvent { event, args ->
                ensureUi {
                    when (event) {
                        ServiceManagerEventType.CLUSTER_CARD_CHANGED -> {
                            currentCard = args[0] as Int
                            lastAppliedConfigs
                                    .clear() // Invalidate cache on card change to force re-sync
                            evaluateJsIfReady(webView, "control('cardId', $currentCard)")
                            updateVirtualClusterVisibility()
                            syncSecondaryDisplayApps(3)
                            MainUiManager.getInstance().handleCardChange(currentCard)
                            if (currentCard == 1 || currentCard == 3) {
                                updateValuesWebView()
                            }
                        }
                        ServiceManagerEventType.STEERING_WHEEL_AC_CONTROL -> {
                            val action = args[0]
                            if (action is SteeringWheelAcControlType) {
                                when (action) {
                                    SteeringWheelAcControlType.FAN_SPEED ->
                                            evaluateJsIfReady(webView, "focus('fan')")
                                    SteeringWheelAcControlType.TEMPERATURE ->
                                            evaluateJsIfReady(webView, "focus('temp')")
                                    SteeringWheelAcControlType.POWER ->
                                            evaluateJsIfReady(webView, "focus('power')")
                                }
                            } else if (action is String) {
                                evaluateJsIfReady(webView, "control('acAction', '$action')")
                            }
                        }
                        ServiceManagerEventType.RADIO_NAVIGATE -> {
                            val now = System.currentTimeMillis()
                            if (now - lastNavTime < 800L) return@ensureUi
                            if (isRadioSeeking) {
                                Log.w(TAG, "RADIO_NAVIGATE blocked — seek in progress")
                                return@ensureUi
                            }
                            lastNavTime = now
                            // Refresh favorites from file async (result applies to next press)
                            scope.launch(Dispatchers.IO) {
                                val xmlFavs = loadFavoritesFromRadioXml()
                                if (xmlFavs.isNotEmpty() && xmlFavs != radioFavorites) {
                                    preferences.edit().putString("cachedRadioFavorites", xmlFavs.joinToString(",")).apply()
                                    withContext(Dispatchers.Main) {
                                        radioFavorites = xmlFavs
                                        Log.w(TAG, "favorites refreshed on navigate: ${xmlFavs.size}")
                                        val jsArr = xmlFavs.map { "\"${"%.1f".format(it / 1000.0)} FM\"" }
                                        evaluateJsIfReady(webView, "control('mediaFavorites', JSON.stringify([${jsArr.joinToString(",")}]))")
                                    }
                                }
                            }
                            val direction = args.getOrNull(0) as? String ?: "next"
                            if (isRadioSource && radioFavorites.isEmpty()) {
                                val raw = ServiceManager.getInstance().getData(CarConstants.SYS_RADIO_FM_FAVORITES_STATION_LIST.value)
                                if (!raw.isNullOrEmpty()) {
                                    val freqs = raw.trim('{', '}').split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
                                    if (freqs.isNotEmpty()) {
                                        radioFavorites = freqs
                                        Log.w(TAG, "RADIO_NAVIGATE lazy load favorites: $freqs")
                                    }
                                }
                            }
                            if (!isRadioSource) {
                                Log.w(TAG, "RADIO_NAVIGATE $direction: ignored — radio not active source")
                                return@ensureUi
                            }
                            if (radioFavorites.isNotEmpty()) {
                                val idx = radioFavorites.indexOf(currentFreqKhz)
                                val nextIdx = if (direction == "next") {
                                    if (idx < 0) 0 else (idx + 1) % radioFavorites.size
                                } else {
                                    if (idx < 0) radioFavorites.size - 1 else (idx - 1 + radioFavorites.size) % radioFavorites.size
                                }
                                val targetFreq = radioFavorites[nextIdx]
                                Log.w(TAG, "RADIO_NAVIGATE $direction: tune to idx=$nextIdx freq=$targetFreq list=${radioFavorites.size} cur=$currentFreqKhz")
                                ServiceManager.getInstance().updateData(CarConstants.SYS_RADIO_CUR_CHANNEL_INFO.value, "{$targetFreq,0,0,0}")
                                // Re-send after 300ms to override any native scan still running
                                handler.postDelayed({
                                    ServiceManager.getInstance().updateData(CarConstants.SYS_RADIO_CUR_CHANNEL_INFO.value, "{$targetFreq,0,0,0}")
                                }, 300)
                            } else {
                                val action = "6"
                                Log.w(TAG, "RADIO_NAVIGATE $direction: seek fallback action=$action favorites=${radioFavorites.size}")
                                ServiceManager.getInstance().updateData(CarConstants.SYS_RADIO_PLAY_CONTROL_ACTION.value, action)
                            }
                        }
                        ServiceManagerEventType.RADIO_PLAY_PAUSE -> {
                            // OK button disabled — hardware "O" button handles play/pause natively
                            Log.d(TAG, "RADIO_PLAY_PAUSE: ignored (hardware O button handles this)")
                        }
                        ServiceManagerEventType.MEDIA_NOTIFICATION_UPDATE -> {
                            if (isRadioSource) return@ensureUi
                            // Startup guard: isRadioSource may not be set yet — confirm via ITS
                            val srcNow = ServiceManager.getInstance().getData(CarConstants.SYS_BASIC_AUDIO_SOURCE_APP.value)
                            val srcNameNow = if (!srcNow.isNullOrEmpty()) getMediaSourceName(srcNow) else ""
                            if (srcNameNow == "FM" || srcNameNow == "AM") {
                                isRadioSource = true
                                Log.w(TAG, "MEDIA_NOTIFICATION_UPDATE: blocked — ITS confirms radio source='$srcNameNow'")
                                return@ensureUi
                            }
                            val source = (args.getOrNull(0) as? String) ?: ""
                            val title = (args.getOrNull(1) as? String) ?: ""
                            val artist = (args.getOrNull(2) as? String) ?: ""
                            val playing = (args.getOrNull(3) as? Boolean) ?: false
                            if (playing) {
                                evaluateJsIfReady(webView, "control('mediaSource', '${source.escapeForJs()}')")
                                evaluateJsIfReady(webView, "control('mediaTitle', '${title.escapeForJs()}')")
                                evaluateJsIfReady(webView, "control('mediaArtist', '${artist.escapeForJs()}')")
                                evaluateJsIfReady(webView, "control('mediaIsPlaying', true)")
                            } else {
                                evaluateJsIfReady(webView, "control('mediaIsPlaying', false)")
                            }
                        }
                        ServiceManagerEventType.GRAPH_SCREEN_NAVIGATION -> {
                            val screen = args[0]
                            if (screen is String) {
                                evaluateJsIfReady(webView, "control('currentGraph','$screen')")
                            }
                        }
                        ServiceManagerEventType.UPDATE_SCREEN -> {
                            val arg0 = args[0]
                            if (arg0 is Screen) {
                                evaluateJsIfReady(webView, "showScreen('${arg0.jsName}')")
                            } else {
                                evaluateJsIfReady(webView, "control('updateScreen', true)")
                            }
                        }
                        ServiceManagerEventType.MENU_ITEM_NAVIGATION -> {
                            val menuNav = args[0] as String
                            evaluateJsIfReady(webView, "control('menuNav', '$menuNav')")
                            evaluateJsIfReady(webView, "focus('$menuNav')")
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
                            if (!isAnyAppOnDisplay3) {
                                lastAppliedConfigs.clear()
                            }
                            updateVirtualClusterVisibility()
                            syncSecondaryDisplayApps(3)
                        }
                        ServiceManagerEventType.DISPLAY_1_APP_STATE_CHANGED -> {
                            isAnyAppOnDisplay1 = args[0] as Boolean
                            Log.w(
                                    TAG,
                                    "Display 1 app state changed in cluster projector: $isAnyAppOnDisplay1"
                            )
                            updateVirtualClusterVisibility()
                        }
                        ServiceManagerEventType.DISMISS_WARNING -> {
                            evaluateJsIfReady(webView, "clearWarnings()")
                            updateWarningUI(false)
                        }
                        ServiceManagerEventType.APP_GEOMETRY_CHANGED -> {
                            updateVirtualClusterVisibility()
                            syncSecondaryDisplayApps(3)
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
        ServiceManager.getInstance().addServiceManagerEventListener(eventListener)
        WebView.setWebContentsDebuggingEnabled(true)
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
        )

        root = FrameLayout(outerContext).apply { setBackgroundColor(Color.TRANSPARENT) }
        setContentView(root)
        setupControlView(root)
        isAnyAppOnDisplay3 =
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isAnyAppOnDisplay(3)
        isAnyAppOnDisplay1 =
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isAnyAppOnDisplay(1)
        updateValuesWebView() // Queue initial values for state sync
        updateVirtualClusterVisibility()
        setupDataListeners()
        // t=3s: check if radio is playing via getData before AA/BT sources kick in
        handler.postDelayed({
            ensureUi {
                if (!isRadioSource) {
                    // Don't override with stale radio state if source is explicitly BT/AA
                    val sm3s = ServiceManager.getInstance()
                    val currentSrc = sm3s.getData(CarConstants.SYS_BASIC_AUDIO_SOURCE_APP.value)
                    val currentSrcName = if (!currentSrc.isNullOrEmpty()) getMediaSourceName(currentSrc) else ""
                    val sourceIsBtOrAa = currentSrcName.isNotEmpty() && currentSrcName != "FM" && currentSrcName != "AM"
                    // Dual-confirm with PLAY_STATE (in DEFAULT_KEYS — live cached value)
                    // SYS_RADIO_CUR_CHANNEL_INFO.playing=1 can be stale; PLAY_STATE reflects actual state
                    val ps3s = sm3s.getData(CarConstants.SYS_RADIO_PLAY_STATE.value)
                    val playStateActive3s = ps3s == "1" || ps3s?.equals("playing", ignoreCase = true) == true
                    Log.w(TAG, "t=3s: source='$currentSrcName' playState='$ps3s' sourceIsBtOrAa=$sourceIsBtOrAa playStateActive=$playStateActive3s")
                    if (!sourceIsBtOrAa && playStateActive3s) {
                        val ch = sm3s.getData(CarConstants.SYS_RADIO_CUR_CHANNEL_INFO.value)
                        if (!ch.isNullOrEmpty()) {
                            val p = ch.trim('{', '}').split(",")
                            val fKhz = p.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
                            val playing = p.getOrNull(3)?.trim() == "1"
                            if (fKhz > 0 && playing) {
                                isRadioSource = true
                                isRadioPlaying = true
                                currentFreqKhz = fKhz
                                val band = if (fKhz >= 76000) "FM" else "AM"
                                val title = "${"%.1f".format(fKhz / 1000.0)} $band"
                                evaluateJsIfReady(webView, "control('mediaSource', '$band')")
                                evaluateJsIfReady(webView, "control('mediaTitle', '${title.escapeForJs()}')")
                                evaluateJsIfReady(webView, "control('mediaArtist', '')")
                                evaluateJsIfReady(webView, "control('mediaIsPlaying', true)")
                            }
                        }
                    }
                }
            }
        }, 3000)
        // t=4s: MediaController and AA poll — isRadioSource already set if radio was detected above
        handler.postDelayed({ setupMediaSessionListener() }, 4000)
        handler.postDelayed(aaPollRunnable, 4000)

        root.isVisible = shouldShowProjector() && ServiceManager.getInstance().isMainScreenOn
    }

    override fun onStop() {
        Log.w(TAG, "onStop: Cleaning up resources")
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(watchdogRunnable)
        handler.removeCallbacks(aaPollRunnable)
        aaLogcatJob?.cancel()
        scope.cancel()
        preferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        ServiceManager.getInstance().removeServiceManagerEventListener(eventListener)

        mediaCallback?.let { currentMediaController?.unregisterCallback(it) }
        mediaCallback = null
        currentMediaController = null
        mediaSessionListener?.let {
            try {
                (context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager)
                    ?.removeOnActiveSessionsChangedListener(it)
            } catch (_: Exception) {}
        }
        mediaSessionListener = null

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
            ensureUi {
                when (key) {
                    CarConstants.CAR_BASIC_VEHICLE_SPEED.value -> {
                        val speedStr = getAdjustedSpeed(value)
                        evaluateJsIfReady(webView, "control('carSpeed', '$speedStr')")
                    }
                    CarConstants.CAR_BASIC_TOTAL_ODOMETER.value -> {
                        evaluateJsIfReady(webView, "control('odometer', '$value')")
                    }
                    CarConstants.CAR_BASIC_REMAIN_FUEL_PERCENTAGE.value -> {
                        evaluateJsIfReady(webView, "control('fuelPercent', '$value')")
                    }
                    CarConstants.CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE.value -> {
                        evaluateJsIfReady(webView, "control('batteryPercent', '$value')")
                    }
                    CarConstants.CAR_EV_INFO_FUEL_MODE_REMAIN_ODOMETER.value -> {
                        evaluateJsIfReady(webView, "control('fuelRange', '$value')")
                    }
                    CarConstants.CAR_EV_INFO_ELECTRIC_MODE_REMAIN_ODOMETER.value -> {
                        evaluateJsIfReady(webView, "control('batteryRange', '$value')")
                    }
                    CarConstants.CAR_BASIC_GEAR_STATUS.value -> {
                        val gear = getGearLabel(value.toString())
                        evaluateJsIfReady(webView, "control('gearState', '$gear')")
                    }
                    CarConstants.CAR_HVAC_FAN_SPEED.value ->
                            evaluateJsIfReady(webView, "control('fan', '$value')")
                    CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.value ->
                            evaluateJsIfReady(webView, "control('temp', '$value')")
                    CarConstants.CAR_HVAC_POWER_MODE.value ->
                            evaluateJsIfReady(webView, "control('power', '$value')")
                    CarConstants.CAR_HVAC_CYCLE_MODE.value ->
                            evaluateJsIfReady(webView, "control('recycle', '$value')")
                    CarConstants.CAR_HVAC_AUTO_ENABLE.value ->
                            evaluateJsIfReady(webView, "control('auto', '$value')")
                    CarConstants.CAR_HVAC_ANION_ENABLE.value ->
                            evaluateJsIfReady(webView, "control('aion', '$value')")
                    CarConstants.CAR_CONFIGURE_DEFAULT_TEMP_UNIT.value -> {
                        val unitLabel = if (value == "1") "°F" else "°C"
                        evaluateJsIfReady(webView, "control('tempUnit', '$unitLabel')")
                    }
                    CarConstants.CAR_BASIC_OUTSIDE_TEMP.value -> {
                        evaluateJsIfReady(
                                webView,
                                "control('outside_temp', ${formatTemp(value.toString())})"
                        )
                    }
                    CarConstants.CAR_BASIC_INSIDE_TEMP.value -> {
                        evaluateJsIfReady(
                                webView,
                                "control('inside_temp', ${formatTemp(value.toString())})"
                        )
                    }
                    CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.value -> {
                        evaluateJsIfReady(
                                webView,
                                "control('evMode', '${MainMenu.EvModeOptions.getLabel(value)}')"
                        )
                    }
                    CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.value -> {
                        val label = MainMenu.DrivingModeOptions.getLabel(value)
                        evaluateJsIfReady(webView, "control('drivingMode', '$label')")
                        evaluateJsIfReady(webView, "control('evModeLabel', '$label')")
                    }
                    CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.value -> {
                        evaluateJsIfReady(
                                webView,
                                "control('steerMode', '${MainMenu.SteerModeOptions.getLabel(value)}')"
                        )
                    }
                    CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE.value -> {
                        evaluateJsIfReady(
                                webView,
                                "control('espStatus', '${MainMenu.EspOptions.getLabel(value)}')"
                        )
                    }
                    CarConstants.CAR_CONFIGURE_PEDAL_CONTROL_ENABLE.value -> {
                        evaluateJsIfReady(webView, "control('onepedal', '${value}')")
                    }
                    CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.value -> {
                        evaluateJsIfReady(
                                webView,
                                "control('regenMode', '${RegenScreen.RegenOptions.getLabel(value)}')"
                        )
                    }
                    CarConstants.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.value -> {
                        val floatVal = value.toString().toFloatOrNull() ?: 0.0f
                        val regenValue = kotlin.math.max(0.0f, -1 * floatVal)
                        evaluateJsIfReady(
                                webView,
                                "control('${GraphicsScreen.GraphOptions.EV_POWER_FACTOR}','$floatVal')"
                        )
                        evaluateJsIfReady(
                                webView,
                                "control('${RegenScreen.RegenOptions.REGEN_GRAPH_STATE_NAME}', '$regenValue')"
                        )
                    }
                    CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.value -> {
                        batteryVoltage = value.toString().toFloatOrNull() ?: 0f
                        val kw = batteryVoltage * batteryCurrent / 1000f
                        evaluateJsIfReady(
                                webView,
                                "control('${GraphicsScreen.GraphOptions.EV_POWER_KW}', '$kw')"
                        )
                    }
                    CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value -> {
                        batteryCurrent = value.toString().toFloatOrNull() ?: 0f
                        val kw = batteryVoltage * batteryCurrent / 1000f
                        evaluateJsIfReady(
                                webView,
                                "control('${GraphicsScreen.GraphOptions.EV_POWER_KW}', '$kw')"
                        )
                    }
                    CarConstants.CAR_BASIC_ENGINE_SPEED.value -> {
                        evaluateJsIfReady(webView, "control('engineRPM', '$value')")
                    }
                    CarConstants.CAR_BASIC_INSTANT_FUEL_CONSUMPTION.value,
                    CarConstants.CAR_EV_INFO_FUEL_CONSUME_INFO.value -> {
                        updateGasConsumption(value)
                    }
                    CarConstants.CAR_EV_INFO_INSTANT_ENERGY_CONSUMPTION.value -> {
                        evaluateJsIfReady(webView, "control('instantEVConsumption', '$value')")
                    }
                    CarConstants.SYS_BASIC_AUDIO_SOURCE_APP.value -> {
                        if (value.toString().isBlank()) return@ensureUi
                        val sourceName = getMediaSourceName(value.toString())
                        val newIsRadio = (sourceName == "FM" || sourceName == "AM")
                        // Always trust SYS_BASIC_AUDIO_SOURCE_APP — it's authoritative for the current source
                        // Do NOT keep isRadioSource=true based on isRadioPlaying; that causes Bug 3
                        isRadioSource = newIsRadio
                        if (isRadioSource) {
                            // Radio is active — read current frequency and use actual band as source
                            val ch = ServiceManager.getInstance().getData(CarConstants.SYS_RADIO_CUR_CHANNEL_INFO.value)
                            if (!ch.isNullOrEmpty()) {
                                val parts = ch.trim('{', '}').split(",")
                                val freqKhz = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
                                if (freqKhz > 0) {
                                    currentFreqKhz = freqKhz
                                    val band = if (freqKhz >= 76000) "FM" else "AM"
                                    val title = "${"%.1f".format(freqKhz / 1000.0)} $band"
                                    evaluateJsIfReady(webView, "control('mediaSource', '$band')")
                                    evaluateJsIfReady(webView, "control('mediaTitle', '${title.escapeForJs()}')")
                                    evaluateJsIfReady(webView, "control('mediaArtist', '')")
                                }
                            }
                            // Re-send favorites so counter appears immediately on source switch
                            if (radioFavorites.isNotEmpty()) {
                                val jsArr = radioFavorites.map { "\"${"%.1f".format(it / 1000.0)} FM\"" }
                                evaluateJsIfReady(webView, "control('mediaFavorites', JSON.stringify([${jsArr.joinToString(",")}]))")
                            } else {
                                scheduleFavoritesRetry()
                            }
                        } else {
                            // Switched to BT/AA — restore cached AA track immediately, then poll for updates
                            lastAaMediaId = ""
                            if (cachedAaTitle.isNotEmpty()) {
                                // Show last known AA track right away — poll will update if song changed
                                Log.w(TAG, "AA source: restoring cached track '$cachedAaTitle'")
                                evaluateJsIfReady(webView, "control('mediaSource', 'AA')")
                                evaluateJsIfReady(webView, "control('mediaTitle', '${cachedAaTitle.escapeForJs()}')")
                                evaluateJsIfReady(webView, "control('mediaArtist', '${cachedAaArtist.escapeForJs()}')")
                                evaluateJsIfReady(webView, "control('mediaIsPlaying', true)")
                            } else {
                                evaluateJsIfReady(webView, "control('mediaSource', '${sourceName.escapeForJs()}')")
                                evaluateJsIfReady(webView, "control('mediaTitle', '')")
                                evaluateJsIfReady(webView, "control('mediaArtist', '')")
                            }
                            val avrcpInfo = ServiceManager.getInstance().getData(CarConstants.SYS_BLUETOOTH_AVRCP_MUSIC_INFO.value)
                            if (!avrcpInfo.isNullOrEmpty()) parseAvrcpMusicInfo(avrcpInfo)
                            scope.launch(Dispatchers.IO) { pollAndroidAutoTrack() }
                        }
                    }
                    CarConstants.SYS_BLUETOOTH_AVRCP_MUSIC_INFO.value -> {
                        Log.w(TAG, "avrcp_music_info raw: $value")
                        parseAvrcpMusicInfo(value.toString())
                    }
                    CarConstants.SYS_BLUETOOTH_AVRCP_PLAY_STATE.value -> {
                        Log.w(TAG, "avrcp_play_state raw: $value")
                        // "1" = playing, "2" = paused, "0" = stopped — works for both BT and AA
                        val playing = value.toString().trim() == "1"
                        if (!isRadioSource) {
                            evaluateJsIfReady(webView, "control('mediaIsPlaying', $playing)")
                        }
                    }
                    CarConstants.SYS_RADIO_CUR_CHANNEL_INFO.value -> {
                        // Format: {freqKhz,?,rdsStatus,playing} e.g. {106300,0,1,1}
                        val raw = value.toString().trim('{', '}')
                        val parts = raw.split(",")
                        val freqKhz = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
                        if (freqKhz > 0) {
                            currentFreqKhz = freqKhz
                            val playing = parts.getOrNull(3)?.trim() == "1"
                            val band = if (freqKhz >= 76000) "FM" else "AM"
                            val title = "${"%.1f".format(freqKhz / 1000.0)} $band"
                            if (isRadioSource) {
                                // Already in radio mode — always update display on channel/preset change
                                // (don't gate on playing=1 since it briefly goes 0 during preset jumps)
                                isRadioPlaying = playing
                                evaluateJsIfReady(webView, "control('mediaSource', '$band')")
                                evaluateJsIfReady(webView, "control('mediaTitle', '${title.escapeForJs()}')")
                                evaluateJsIfReady(webView, "control('mediaArtist', '')")
                                evaluateJsIfReady(webView, "control('mediaIsPlaying', $playing)")
                            } else if (playing) {
                                // playing=1 but isRadioSource=false — this value CAN be stale!
                                // (SYS_RADIO_CUR_CHANNEL_INFO retains playing=1 even after radio stops)
                                // Dual-confirm with SYS_RADIO_PLAY_STATE (DEFAULT_KEYS — reliable live value)
                                val ps = ServiceManager.getInstance().getData(CarConstants.SYS_RADIO_PLAY_STATE.value)
                                val playStateActive = ps == "1" || ps?.equals("playing", ignoreCase = true) == true
                                if (!playStateActive) {
                                    Log.w(TAG, "SYS_RADIO_CUR_CHANNEL_INFO playing=1 but PLAY_STATE='$ps' — stale, ignoring to protect AA/BT display")
                                    return@ensureUi
                                }
                                isRadioSource = true
                                isRadioPlaying = true
                                evaluateJsIfReady(webView, "control('mediaSource', '$band')")
                                evaluateJsIfReady(webView, "control('mediaTitle', '${title.escapeForJs()}')")
                                evaluateJsIfReady(webView, "control('mediaArtist', '')")
                                evaluateJsIfReady(webView, "control('mediaIsPlaying', true)")
                                if (radioFavorites.isEmpty()) scheduleFavoritesRetry()
                            }
                        }
                    }
                    CarConstants.SYS_RADIO_PLAY_STATE.value -> {
                        val playing = value == "1" || value.toString().equals("playing", ignoreCase = true)
                        isRadioPlaying = playing
                        evaluateJsIfReady(webView, "control('mediaIsPlaying', $playing)")
                        if (playing) {
                            // Radio resumed/started — ensure source is set and update display
                            isRadioSource = true
                            val sm2 = ServiceManager.getInstance()
                            val ch = sm2.getData(CarConstants.SYS_RADIO_CUR_CHANNEL_INFO.value)
                            if (!ch.isNullOrEmpty()) {
                                val parts = ch.trim('{', '}').split(",")
                                val freqKhz = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
                                if (freqKhz > 0) {
                                    currentFreqKhz = freqKhz
                                    val band = if (freqKhz >= 76000) "FM" else "AM"
                                    val title = "${"%.1f".format(freqKhz / 1000.0)} $band"
                                    evaluateJsIfReady(webView, "control('mediaSource', '$band')")
                                    evaluateJsIfReady(webView, "control('mediaTitle', '${title.escapeForJs()}')")
                                    evaluateJsIfReady(webView, "control('mediaArtist', '')")
                                }
                            }
                            // Re-send favorites so counter reappears after pause/play
                            if (radioFavorites.isNotEmpty()) {
                                val jsArr = radioFavorites.map { "\"${"%.1f".format(it / 1000.0)} FM\"" }
                                evaluateJsIfReady(webView, "control('mediaFavorites', JSON.stringify([${jsArr.joinToString(",")}]))")
                            }
                        } else {
                            // Radio paused/stopped — DON'T clear mediaTitle (counter would disappear)
                            isRadioSource = false
                            // Reset so next AA poll re-dispatches the same track (doesn't skip it)
                            lastAaMediaId = ""
                            // Immediate poll so AA track appears without waiting up to 2s
                            scope.launch(Dispatchers.IO) { pollAndroidAutoTrack() }
                        }
                    }
                    CarConstants.SYS_RADIO_FM_FAVORITES_STATION_LIST.value -> {
                        val raw = value.toString().trim('{', '}')
                        val freqList = raw.split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
                        Log.w(TAG, "fm_favorites_station_list: raw=$raw freqs=${freqList.size} list=$freqList")
                        if (freqList.isNotEmpty()) {
                            radioFavorites = freqList
                            preferences.edit().putString("cachedRadioFavorites", raw).apply()
                            val jsArr = freqList.map { "\"${"%.1f".format(it / 1000.0)} FM\"" }
                            evaluateJsIfReady(webView, "control('mediaFavorites', JSON.stringify([${jsArr.joinToString(",")}]))")
                        }
                    }
                    CarConstants.SYS_RADIO_SEARCH_STATE.value -> {
                        isRadioSeeking = value.toString() == "3"
                        Log.w(TAG, "search_state=$value isRadioSeeking=$isRadioSeeking")
                    }
                    CarConstants.SYS_OTHER_KEYEVENT_NOTIFY.value -> {
                        val raw = value.toString().trim('{', '}')
                        val parts = raw.split(",")
                        val code = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@ensureUi
                        val action = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                        if (code == 1004 && action == 0 && isRadioSource && currentFreqKhz > 0) {
                            Log.w(TAG, "FAVORITE: long-press O → salvando freq=$currentFreqKhz via Shizuku")
                            scope.launch(Dispatchers.IO) {
                                val ok = addFavoriteToRadioXml(currentFreqKhz)
                                if (ok) {
                                    val updated = loadFavoritesFromRadioXml()
                                    withContext(Dispatchers.Main) {
                                        radioFavorites = updated
                                        val jsArr = updated.map { "\"${"%.1f".format(it / 1000.0)} FM\"" }
                                        evaluateJsIfReady(webView, "control('mediaFavorites', JSON.stringify([${jsArr.joinToString(",")}]))")
                                        Log.w(TAG, "FAVORITE: salvo! lista atualizada: ${updated.size}")
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Warning Management Logic ---
                if (key in monitoredWarningKeys) {
                    evaluateJsIfReady(webView, "updateWarning('$key', '$value')")
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
                        ?.let { config ->
                            Log.d(TAG, "Auto-launching default app: $defaultPackage")
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
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.let { wv: android.webkit.WebView ->
                                            Log.w(
                                                    TAG,
                                                    "WebView finished loading (PID: ${android.os.Process.myPid()}): $url"
                                            )

                                            // Apply pending JS or updates
                                            updateValuesWebView()
                                            // Retry: if radio is playing but isRadioSource never got confirmed, fix it
                                            handler.postDelayed({
                                                ensureUi {
                                                    if (!isRadioSource) {
                                                        // Don't override with stale radio if source says BT/AA
                                                        val smPf = ServiceManager.getInstance()
                                                        val curSrc2 = smPf.getData(CarConstants.SYS_BASIC_AUDIO_SOURCE_APP.value)
                                                        val curSrcName2 = if (!curSrc2.isNullOrEmpty()) getMediaSourceName(curSrc2) else ""
                                                        val srcIsBtOrAa2 = curSrcName2.isNotEmpty() && curSrcName2 != "FM" && curSrcName2 != "AM"
                                                        val psPf = smPf.getData(CarConstants.SYS_RADIO_PLAY_STATE.value)
                                                        val playStatePf = psPf == "1" || psPf?.equals("playing", ignoreCase = true) == true
                                                        Log.w(TAG, "onPageFinished retry: source='$curSrcName2' playState='$psPf' srcIsBtOrAa=$srcIsBtOrAa2")
                                                        if (!srcIsBtOrAa2 && playStatePf) {
                                                            val ch2 = smPf.getData(CarConstants.SYS_RADIO_CUR_CHANNEL_INFO.value)
                                                            if (!ch2.isNullOrEmpty()) {
                                                                val p = ch2.trim('{', '}').split(",")
                                                                val fKhz = p.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
                                                                val playing2 = p.getOrNull(3)?.trim() == "1"
                                                                if (fKhz > 0 && playing2) {
                                                                    isRadioSource = true
                                                                    isRadioPlaying = true
                                                                    currentFreqKhz = fKhz
                                                                    val band2 = if (fKhz >= 76000) "FM" else "AM"
                                                                    val title2 = "${"%.1f".format(fKhz / 1000.0)} $band2"
                                                                    evaluateJsIfReady(webView, "control('mediaSource', '$band2')")
                                                                    evaluateJsIfReady(webView, "control('mediaTitle', '${title2.escapeForJs()}')")
                                                                    evaluateJsIfReady(webView, "control('mediaArtist', '')")
                                                                    evaluateJsIfReady(webView, "control('mediaIsPlaying', true)")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }, 2000)
                                            webViewsLoaded[wv] = true
                                            pendingJsQueues[wv]?.let { list ->
                                                for (js in list) {
                                                    wv.evaluateJavascript(js, null)
                                                }
                                                pendingJsQueues.remove(wv)
                                            }
                                            // Inject Heartbeat
                                            wv.evaluateJavascript(
                                                    "setInterval(() => { if (window.Android && window.Android.heartbeat) window.Android.heartbeat(); }, 2000);",
                                                    null
                                            )
                                        }
                                    }
                                }
                        loadDataWithBaseURL(
                                getThemeBaseUrl(),
                                readAppContent(outerContext),
                                "text/html",
                                "UTF-8",
                                null
                        )
                        addJavascriptInterface(WebAppInterface(), "Android")
                    }
            parent.addView(webView)
        }
    }

    private fun updateValuesWebView() {
        val sm = ServiceManager.getInstance()
        val webView = this.webView
        if (webView == null) return

        val updates = mutableMapOf<String, String>()

        updates["display"] = getSavedClusterDisplay()
        updates["mediaBarEnabled"] = preferences.getBoolean(SharedPreferencesKeys.CLUSTER_MEDIA_BAR_ENABLED.key, true).toString()

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
        updates[GraphicsScreen.GraphOptions.CAR_SPEED] = speedStr
        updates["engineRPM"] = sm.getData(CarConstants.CAR_BASIC_ENGINE_SPEED.value) ?: "0"

        // Modes and Settings
        val evMode = sm.getData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.value)
        updates["evMode"] = MainMenu.EvModeOptions.getLabel(evMode)

        val drivingMode = sm.getData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.value)
        val drivingModeLabel = MainMenu.DrivingModeOptions.getLabel(drivingMode)
        updates["drivingMode"] = drivingModeLabel
        updates["evModeLabel"] = drivingModeLabel

        val steerMode = sm.getData(CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.value)
        updates["steerMode"] = MainMenu.SteerModeOptions.getLabel(steerMode)

        val espStatus = sm.getData(CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE.value)
        updates["espStatus"] = MainMenu.EspOptions.getLabel(espStatus)

        val regenLevel = sm.getData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.value)
        updates["regenMode"] = RegenScreen.RegenOptions.getLabel(regenLevel)

        // Power and Regen Graph
        val outputPower =
                sm.getData(CarConstants.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.value)?.toFloatOrNull()
                        ?: 0.0f
        val regenValue = kotlin.math.max(0.0f, -1 * outputPower)
        updates[GraphicsScreen.GraphOptions.EV_POWER_FACTOR] = outputPower.toString()
        updates[RegenScreen.RegenOptions.REGEN_GRAPH_STATE_NAME] = regenValue.toString()

        // Battery KW Calculation
        batteryVoltage =
                sm.getData(CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.value)?.toFloatOrNull()
                        ?: 0f
        batteryCurrent =
                sm.getData(CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value)?.toFloatOrNull() ?: 0f
        val kw = batteryVoltage * batteryCurrent / 1000f
        updates[GraphicsScreen.GraphOptions.EV_POWER_KW] = kw.toString()

        // Consumption initial values
        updateGasConsumption(
                sm.getData(CarConstants.CAR_BASIC_INSTANT_FUEL_CONSUMPTION.value),
                updates
        )
        updates["instantEVConsumption"] =
                sm.getData(CarConstants.CAR_EV_INFO_INSTANT_ENERGY_CONSUMPTION.value) ?: "0"

        batchEvaluateJs(webView, updates)

        // Media initial values — only show radio if it's actually the active source AND playing
        val audioSourceInit = sm.getData(CarConstants.SYS_BASIC_AUDIO_SOURCE_APP.value)
        val sourceNameInit = if (!audioSourceInit.isNullOrEmpty()) getMediaSourceName(audioSourceInit) else ""
        val initIsRadio = (sourceNameInit == "FM" || sourceNameInit == "AM")
        // If source is explicitly BT/AA, set isRadioSource=false regardless of isRadioPlaying
        // If source is unknown (blank), preserve existing isRadioSource until ITS events arrive
        if (sourceNameInit.isNotEmpty()) {
            isRadioSource = initIsRadio
        }
        val channelInfo = sm.getData(CarConstants.SYS_RADIO_CUR_CHANNEL_INFO.value)
        val radioPlaying = channelInfo?.trim('{','}')?.split(",")?.getOrNull(3)?.trim() == "1"
        // Only trust radio playing=1 if source is FM/AM or unknown (blank)
        // If source explicitly says BT/AA, don't override — that's Bug 3
        val sourceExplicitlyBtAa = sourceNameInit.isNotEmpty() && !initIsRadio
        // Double-confirm with SYS_RADIO_PLAY_STATE (in DEFAULT_KEYS, reliable cache):
        // SYS_RADIO_CUR_CHANNEL_INFO.playing=1 can be stale; PLAY_STATE reflects actual current state
        val radioPlayState = sm.getData(CarConstants.SYS_RADIO_PLAY_STATE.value)
        val radioPlayStateActive = radioPlayState == "1" || radioPlayState?.equals("playing", ignoreCase = true) == true
        Log.w(TAG, "updateValuesWebView: source='$sourceNameInit' chanPlaying=$radioPlaying playState='$radioPlayState' sourceExplicitlyBtAa=$sourceExplicitlyBtAa")
        if (!channelInfo.isNullOrEmpty() && radioPlaying && radioPlayStateActive && !sourceExplicitlyBtAa) {
            if (!isRadioSource) isRadioSource = true
            val raw = channelInfo.trim('{', '}')
            val parts = raw.split(",")
            val freqKhz = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
            if (freqKhz > 0) {
                val band = if (freqKhz >= 76000) "FM" else "AM"
                val title = "${"%.1f".format(freqKhz / 1000.0)} $band"
                evaluateJsIfReady(webView, "control('mediaSource', '$band')")
                evaluateJsIfReady(webView, "control('mediaTitle', '${title.escapeForJs()}')")
                evaluateJsIfReady(webView, "control('mediaArtist', '')")
                val playing = parts.getOrNull(3)?.trim() == "1"
                evaluateJsIfReady(webView, "control('mediaIsPlaying', $playing)")
            }
        } else if (sourceNameInit.isNotEmpty() && !isRadioSource) {
            // Only populate BT/AA if we KNOW the source is not radio.
            // If sourceNameInit is empty (VHAL not ready yet), do nothing — ITS events + delayed retry will populate.
            evaluateJsIfReady(webView, "control('mediaSource', '${sourceNameInit.escapeForJs()}')")
            val avrcpInfo = sm.getData(CarConstants.SYS_BLUETOOTH_AVRCP_MUSIC_INFO.value)
            if (!avrcpInfo.isNullOrEmpty()) {
                Log.w(TAG, "avrcp_music_info init: $avrcpInfo")
                parseAvrcpMusicInfo(avrcpInfo)
            }
        }
        scope.launch(Dispatchers.IO) {
            val xmlFavs = loadFavoritesFromRadioXml()
            if (xmlFavs.isNotEmpty()) {
                preferences.edit().putString("cachedRadioFavorites", xmlFavs.joinToString(",")).apply()
                withContext(Dispatchers.Main) {
                    radioFavorites = xmlFavs
                    val jsArr = xmlFavs.map { "\"${"%.1f".format(it / 1000.0)} FM\"" }
                    evaluateJsIfReady(webView, "control('mediaFavorites', JSON.stringify([${jsArr.joinToString(",")}]))")
                }
            }
        }
        val favList = sm.getData(CarConstants.SYS_RADIO_FM_FAVORITES_STATION_LIST.value)
        val favSource: List<Long>
        if (!favList.isNullOrEmpty()) {
            val freqs = favList.trim('{', '}').split(",")
                .mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
            if (freqs.isNotEmpty()) {
                radioFavorites = freqs
                preferences.edit().putString("cachedRadioFavorites", favList.trim('{', '}')).apply()
                Log.w(TAG, "favorites loaded from ITS getData: ${freqs.size}")
            }
            favSource = freqs
        } else {
            val cached = preferences.getString("cachedRadioFavorites", null)
            favSource = if (!cached.isNullOrEmpty()) {
                val freqs = cached.split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
                if (freqs.isNotEmpty()) {
                    radioFavorites = freqs
                    Log.w(TAG, "favorites loaded from cache: ${freqs.size}")
                }
                freqs
            } else emptyList()
        }
        if (favSource.isNotEmpty()) {
            val jsArr = favSource.map { "\"${"%.1f".format(it / 1000.0)} FM\"" }
            evaluateJsIfReady(webView, "control('mediaFavorites', JSON.stringify([${jsArr.joinToString(",")}]))")
        }
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

    private fun updateVirtualClusterVisibility() {
        val clusterEnabled =
                preferences.getBoolean(SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key, true)
        var isLeftCovered = false
        var isRightCovered = false

        val configs = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAllConfigs()

        for (displayId in listOf(1, 3)) {
            val res =
                    br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                            .getDisplayResolution(displayId)
            val fullWidth = res.first
            if (fullWidth <= 0) continue

            val appsOnDisplay =
                    configs.filter { config ->
                        val task =
                                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                        .findTaskForPackage(config.packageName)
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
                        if (displayId == 3 && (currentCard == 0 || isWarningActive)) {
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

        evaluateJsIfReady(webView, "control('clusterEnabled', $clusterEnabled)")
        evaluateJsIfReady(webView, "control('appInDash', $appInDashValue)")
    }

    private fun syncSecondaryDisplayApps(displayId: Int) {
        val res =
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getDisplayResolution(
                        displayId
                )
        val fullWidth = res.first
        if (fullWidth <= 0) return

        val appsOnDisplay =
                br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAllConfigs()
                        .filter { config ->
                            val task =
                                    br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                            .findTaskForPackage(config.packageName)
                            task != null &&
                                    task.displayId == displayId &&
                                    (config.packageName != outerContext.packageName ||
                                            displayId != 1)
                        }

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
                    if (displayId == 3 && (currentCard == 0 || isWarningActive)) {
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

    private fun String.escapeForJs() = replace("\\", "\\\\").replace("'", "\\'")

    private fun addFavoriteToRadioXml(freqKhz: Long): Boolean {
        return try {
            val path = "/data/user_de/0/com.beantechs.mediacenter/shared_prefs/local_radio.xml"
            val xml = ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", "cat $path")) ?: return false
            val line = xml.lines().firstOrNull { it.contains("fm_favorites_station_list") } ?: return false
            val match = Regex("""\{([^}]+)\}""").find(line) ?: return false
            val existing = match.groupValues[1].split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
            if (freqKhz in existing) {
                Log.w(TAG, "FAVORITE: $freqKhz já está na lista")
                return true
            }
            val newValue = "{${(existing + freqKhz).joinToString(",")}}"
            val oldValue = match.value
            val sed = "sed -i 's|$oldValue|$newValue|g' $path"
            ShizukuUtils.runCommandAndGetOutput(arrayOf("sh", "-c", sed))
            Log.w(TAG, "FAVORITE: adicionado $freqKhz -> lista nova: $newValue")
            true
        } catch (e: Exception) {
            Log.w(TAG, "addFavoriteToRadioXml falhou: ${e.message}")
            false
        }
    }

    private fun loadFavoritesFromRadioXml(): List<Long> {
        return try {
            val xml = ShizukuUtils.runCommandAndGetOutput(
                arrayOf("sh", "-c", "cat /data/user_de/0/com.beantechs.mediacenter/shared_prefs/local_radio.xml")
            ) ?: return emptyList()
            val line = xml.lines().firstOrNull { it.contains("fm_favorites_station_list") } ?: return emptyList()
            val match = Regex("""\{([^}]+)\}""").find(line) ?: return emptyList()
            val freqs = match.groupValues[1].split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
            Log.w(TAG, "favorites from local_radio.xml: ${freqs.size} -> $freqs")
            freqs
        } catch (e: Exception) {
            Log.w(TAG, "loadFavoritesFromRadioXml failed: ${e.message}")
            emptyList()
        }
    }

    private fun scheduleFavoritesRetry() {
        scope.launch(Dispatchers.IO) {
            repeat(30) { attempt ->
                delay(2000L)
                if (radioFavorites.isNotEmpty()) return@launch
                val fromXml = loadFavoritesFromRadioXml()
                if (fromXml.isNotEmpty()) {
                    preferences.edit().putString("cachedRadioFavorites", fromXml.joinToString(",")).apply()
                    withContext(Dispatchers.Main) {
                        radioFavorites = fromXml
                        Log.w(TAG, "favorites loaded from xml on retry ${attempt + 1}: $fromXml")
                        val jsArr = fromXml.map { "\"${"%.1f".format(it / 1000.0)} FM\"" }
                        evaluateJsIfReady(webView, "control('mediaFavorites', JSON.stringify([${jsArr.joinToString(",")}]))")
                    }
                    return@launch
                }
                val raw = ServiceManager.getInstance().getData(CarConstants.SYS_RADIO_FM_FAVORITES_STATION_LIST.value)
                if (!raw.isNullOrEmpty()) {
                    val freqs = raw.trim('{', '}').split(",")
                        .mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
                    if (freqs.isNotEmpty()) {
                        preferences.edit().putString("cachedRadioFavorites", raw.trim('{', '}')).apply()
                        withContext(Dispatchers.Main) {
                            radioFavorites = freqs
                            Log.w(TAG, "favorites loaded on retry ${attempt + 1}: $freqs")
                            val jsArr = freqs.map { "\"${"%.1f".format(it / 1000.0)} FM\"" }
                            evaluateJsIfReady(webView, "control('mediaFavorites', JSON.stringify([${jsArr.joinToString(",")}]))")
                        }
                        return@launch
                    }
                }
                Log.w(TAG, "favorites retry ${attempt + 1} — still empty")
            }
        }
    }

    private fun getMediaSourceName(value: String): String {
        return when {
            value.contains("spotify", ignoreCase = true) -> "Spotify"
            value.contains("youtube", ignoreCase = true) -> "YouTube"
            value.contains("bluetooth", ignoreCase = true) || value == "3" -> "BT"
            value.contains("fm", ignoreCase = true) || value == "1" || value == "10" -> "FM"
            value.contains("am", ignoreCase = true) || value == "2" || value == "11" -> "AM"
            value.contains("usb", ignoreCase = true) || value == "5" -> "USB"
            value == "4" -> "CarLife"
            value.contains(".") -> value.substringAfterLast('.').take(10).uppercase()
            value.isNotBlank() -> value.take(10).uppercase()
            else -> "—"
        }
    }


    private fun pollAndroidAutoTrack() {
        try {
            val out = ShizukuUtils.runCommandAndGetOutput(
                arrayOf("sh", "-c", "logcat -t 2000 | grep 'savePlayMediaInfo json' | tail -1")
            ) ?: return
            if (out.isBlank()) return
            parseAaLogLine(out)
        } catch (e: Exception) {
            Log.e(TAG, "AA poll error: $e")
        }
    }

    private fun parseAaLogLine(line: String) {
        if (isRadioSource) return
        try {
            val jsonStart = line.indexOf('{')
            if (jsonStart < 0) return
            val json = line.substring(jsonStart)
            if (!json.contains("ANDROID_AUTO")) return
            val obj = org.json.JSONObject(json)
            val mediaId = obj.optString("mediaId")
            if (mediaId == lastAaMediaId) return
            lastAaMediaId = mediaId
            val title = obj.optString("title").takeIf { it.isNotBlank() &&
                !it.equals("not provided", ignoreCase = true) } ?: return
            val author = obj.optString("author")
            Log.w(TAG, "AA track: title=$title author=$author")
            cachedAaTitle = title
            cachedAaArtist = author
            ensureUi {
                evaluateJsIfReady(webView, "control('mediaSource', 'AA')")
                evaluateJsIfReady(webView, "control('mediaTitle', '${title.escapeForJs()}')")
                evaluateJsIfReady(webView, "control('mediaArtist', '${author.escapeForJs()}')")
                evaluateJsIfReady(webView, "control('mediaIsPlaying', true)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AA parse error: $e")
        }
    }

    private fun parseAvrcpMusicInfo(raw: String) {
        if (raw.isBlank()) return
        if (isRadioSource) return
        var title: String? = null
        var artist: String? = null
        try {
            if (raw.trimStart().startsWith("{")) {
                val obj = org.json.JSONObject(raw)
                title = obj.optString("title").ifBlank { null }
                artist = obj.optString("author").ifBlank { obj.optString("artist").ifBlank { null } }
            } else {
                title = Regex("title=([^,}]+)").find(raw)?.groupValues?.get(1)?.trim()
                artist = Regex("(?:author|artist)=([^,}]+)").find(raw)?.groupValues?.get(1)?.trim()
            }
        } catch (_: Exception) {}
        Log.w(TAG, "avrcp_music_info parsed: title=$title artist=$artist")
        if (!title.isNullOrBlank() &&
            !title.equals("not provided", ignoreCase = true) &&
            !title.equals("null", ignoreCase = true)) {
            evaluateJsIfReady(webView, "control('mediaSource', 'BT')")
            evaluateJsIfReady(webView, "control('mediaTitle', '${title.escapeForJs()}')")
            evaluateJsIfReady(webView, "control('mediaArtist', '${(artist ?: "").escapeForJs()}')")
        }
    }

    private fun setupMediaSessionListener() {
        try {
            val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            mediaSessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                handleActiveSessionsChanged(controllers)
            }
            mgr.addOnActiveSessionsChangedListener(mediaSessionListener!!, null)
            handleActiveSessionsChanged(mgr.getActiveSessions(null))
        } catch (_: SecurityException) {
            // MEDIA_CONTENT_CONTROL not granted — radio AIDL only
        }
    }

    private fun handleActiveSessionsChanged(controllers: List<MediaController>?) {
        mediaCallback?.let { currentMediaController?.unregisterCallback(it) }
        mediaCallback = null
        currentMediaController = null
        val controller = controllers?.firstOrNull() ?: return
        currentMediaController = controller
        mediaCallback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { updateFromMetadata(metadata) }
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (isRadioSource) return
                val playing = state?.state == PlaybackState.STATE_PLAYING
                ensureUi { evaluateJsIfReady(webView, "control('mediaIsPlaying', $playing)") }
            }
        }
        controller.registerCallback(mediaCallback!!)
        updateFromMetadata(controller.metadata)
        if (!isRadioSource) {
            val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            ensureUi { evaluateJsIfReady(webView, "control('mediaIsPlaying', $playing)") }
        }
    }

    private fun updateFromMetadata(metadata: MediaMetadata?) {
        if (isRadioSource) return
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.escapeForJs() ?: ""
        val artist = (metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.escapeForJs() ?: ""
        ensureUi {
            if (title.isNotEmpty()) evaluateJsIfReady(webView, "control('mediaTitle', '$title')")
            evaluateJsIfReady(webView, "control('mediaArtist', '$artist')")
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

    private fun getThemeBaseUrl(): String {
        val customThemeName =
                preferences.getString(SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, "") ?: ""
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

        val customThemeName =
                preferences.getString(SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, "") ?: ""
        if (customThemeName.isNotEmpty()) {
            try {
                val themeManager =
                        br.com.redesurftank.havalshisuku.managers.ThemeManager.getInstance(
                                outerContext
                        )
                val metadata = themeManager.getThemeMetadata(customThemeName)
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
        }

        Log.d(TAG, "Loading base HTML from resource: app.html")
        return context.resources.openRawResource(R.raw.app).bufferedReader().use { it.readText() }
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
        ensureUi { root.visibility = View.VISIBLE }
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
            updates[GraphicsScreen.GraphOptions.GAS_CONSUMPTION_MODE] = mode
            updates[GraphicsScreen.GraphOptions.GAS_CONSUMPTION_IDLE] = adjustedValueIdle.toString()
            updates[GraphicsScreen.GraphOptions.GAS_CONSUMPTION] = adjustedValue.toString()
        } else {
            evaluateJsIfReady(
                    view,
                    "control('${GraphicsScreen.GraphOptions.GAS_CONSUMPTION_MODE}', '$mode')"
            )
            evaluateJsIfReady(
                    view,
                    "control('${GraphicsScreen.GraphOptions.GAS_CONSUMPTION_IDLE}', $adjustedValueIdle)"
            )
            evaluateJsIfReady(
                    view,
                    "control('${GraphicsScreen.GraphOptions.GAS_CONSUMPTION}', $adjustedValue)"
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
        val savedDisplay =
                preferences.getString(
                        SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.key,
                        "Normal"
                ) ?: "Normal"

        return normalizeClusterDisplay(savedDisplay)
    }

    private fun normalizeClusterDisplay(display: String): String {
        return when (display) {
            "Normal", "Esportivo", "Reduzido", "Clean" -> display
            else -> "Normal"
        }
    }

    private fun saveClusterDisplay(display: String) {
        val normalizedDisplay = normalizeClusterDisplay(display)
        preferences.edit()
                .putString(SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.key, normalizedDisplay)
                .apply()
        Log.d(TAG, "Cluster display saved: $normalizedDisplay")
    }

    private fun updateWarningUI(anyWarningActive: Boolean) {
        isWarningActive = anyWarningActive
        lastAppliedConfigs.clear() // Invalidate cache on warning toggle to force re-sync
        if (anyWarningActive) {
            Log.w(TAG, "Warning detected. currentCard=$currentCard. Triggering visibility update.")
        } else {
            Log.w(TAG, "Warnings cleared.")
        }

        updateVirtualClusterVisibility()
        syncSecondaryDisplayApps(3)

        // Propagate current warning state
        evaluateJsIfReady(webView, "control('warningActive', $anyWarningActive)")
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun heartbeat() {
            lastHeartbeatTime = System.currentTimeMillis()
        }

        @JavascriptInterface
        fun setWarningActive(isActive: Boolean) {
            ensureUi { updateWarningUI(isActive) }
        }

        @JavascriptInterface
        fun setCardId(cardId: Int) {
            currentCard = cardId
            Log.d(TAG, "Card ID updated to $cardId")
            syncSecondaryDisplayApps(3)
        }

        @JavascriptInterface
        fun saveSetting(key: String, value: String) {
            when (key) {
                SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.key -> saveClusterDisplay(value)
                SharedPreferencesKeys.CLUSTER_MEDIA_BAR_ENABLED.key -> {
                    val enabled = value == "true"
                    preferences.edit().putBoolean(SharedPreferencesKeys.CLUSTER_MEDIA_BAR_ENABLED.key, enabled).apply()
                    Log.d(TAG, "Media bar enabled saved: $enabled")
                }
                else -> Log.w(TAG, "Ignoring unsupported WebView setting: $key")
            }
        }
    }
}
