package br.com.redesurftank.havalshisuku.projectors

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
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

        root.isVisible = shouldShowProjector() && ServiceManager.getInstance().isMainScreenOn
    }

    override fun onStop() {
        Log.w(TAG, "onStop: Cleaning up resources")
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(watchdogRunnable)
        scope.cancel()
        preferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
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
    }

    private fun batchEvaluateJs(view: WebView?, updates: Map<String, String>) {
        if (view == null || updates.isEmpty()) return
        val jsBuilder = StringBuilder("(function(){")
        updates.forEach { (key, value) ->
            val formattedValue = if (value == "true" || value == "false") value else "'$value'"
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
                else -> Log.w(TAG, "Ignoring unsupported WebView setting: $key")
            }
        }
    }
}
