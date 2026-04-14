package br.com.redesurftank.havalshisuku.projectors

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Outline
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.SteeringWheelAcControlType
import br.com.redesurftank.havalshisuku.models.screens.GraphicsScreen
import br.com.redesurftank.havalshisuku.models.screens.MainMenu
import br.com.redesurftank.havalshisuku.models.screens.RegenScreen
import br.com.redesurftank.havalshisuku.models.screens.Screen
import kotlin.math.roundToInt

class InstrumentProjector2(outerContext: Context, display: Display) : BaseProjector(outerContext, display) {
    private val preferences: SharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    private var webView: WebView? = null
    private val webViewsLoaded = mutableMapOf<WebView, Boolean>()
    private val pendingJsQueues = mutableMapOf<WebView, MutableList<String>>()
    private lateinit var root: FrameLayout;
    private var lastModified: Long = 0

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in listOf(
                SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.key,
                SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.key
            )
        ) {
            ensureUi {
                root.isVisible = shouldShowProjector() && ServiceManager.getInstance().isMainScreenOn
            }
        }
    }

    private fun shouldShowProjector(): Boolean {
        return preferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.key, false) && preferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.key, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences.registerOnSharedPreferenceChangeListener(prefsListener)
        WebView.setWebContentsDebuggingEnabled(true)
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        window?.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        root = FrameLayout(context)
        setContentView(root)

        val radius = 226
        val centerX = 1630
        val centerY = 430

        val circularView = FrameLayout(context)
        val params = FrameLayout.LayoutParams(radius * 2, radius * 2)
        params.leftMargin = centerX - radius
        params.topMargin = centerY - radius
        circularView.layoutParams = params
        circularView.setBackgroundColor(Color.TRANSPARENT)
        circularView.clipToOutline = true
        circularView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        root.addView(circularView)
        circularView.isVisible = false
        setupAcControlView(circularView)

        ServiceManager.getInstance().addDataChangedListener { key, value ->
            ensureUi {
                when (key) {
                    CarConstants.CAR_HVAC_FAN_SPEED.value -> {
                        evaluateJsIfReady(webView, "control('fan', $value)")
                    }

                    CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.value -> {
                        evaluateJsIfReady(webView, "control('temp', $value)")
                    }

                    CarConstants.CAR_HVAC_POWER_MODE.value -> {
                        evaluateJsIfReady(webView, "control('power', $value)")
                    }

                    CarConstants.CAR_HVAC_CYCLE_MODE.value -> {
                        evaluateJsIfReady(webView, "control('recycle', $value)")
                    }

                    CarConstants.CAR_HVAC_AUTO_ENABLE.value -> {
                        evaluateJsIfReady(webView, "control('auto', $value)")
                    }

                    CarConstants.CAR_HVAC_ANION_ENABLE.value -> {
                        evaluateJsIfReady(webView, "control('aion', $value)")
                    }

                    CarConstants.CAR_BASIC_OUTSIDE_TEMP.value -> {
                        evaluateJsIfReady(webView, "control('outside_temp', ${value.toFloat().roundToInt()})")
                    }

                    CarConstants.CAR_BASIC_INSIDE_TEMP.value -> {
                        evaluateJsIfReady(webView, "control('inside_temp', ${value.toFloat().roundToInt()})")
                    }

                    CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.value -> {
                        evaluateJsIfReady(webView, "control('evMode', ${MainMenu.EvModeOptions.getLabel(value)})")
                    }

                    CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.value -> {
                        evaluateJsIfReady(webView, "control('drivingMode', ${MainMenu.DrivingModeOptions.getLabel(value)})")
                    }

                    CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.value -> {
                        evaluateJsIfReady(webView, "control('steerMode', ${MainMenu.SteerModeOptions.getLabel(value)})")
                    }

                    CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE.value -> {
                        evaluateJsIfReady (webView, "control('espStatus', ${MainMenu.EspOptions.getLabel(value)})")
                    }

                    CarConstants.CAR_CONFIGURE_PEDAL_CONTROL_ENABLE.value -> {
                        evaluateJsIfReady(webView, "control('onepedal', ${value == "1"})")                    }

                    CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.value -> {
                        evaluateJsIfReady (webView, "control('regenMode', ${RegenScreen.RegenOptions.getLabel(value)})")
                    }

                    CarConstants.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.value -> {
                        val regenValue = kotlin.math.max(0.0f,-1 * (value).toFloat())
                        evaluateJsIfReady (webView, "control('${GraphicsScreen.GraphOptions.EV_CONSUMPTION}',$value)")
                        evaluateJsIfReady (webView, "control('${RegenScreen.RegenOptions.REGEN_GRAPH_STATE_NAME}', $regenValue)")

                    }

                    CarConstants.CAR_BASIC_INSTANT_FUEL_CONSUMPTION.value -> {
                        val stringValue = value.toString()
                        var metricValue = 0.0f
                        var consumptionValue = 0.0f
                        var consumptionMetric = "km/l"
                        var consumptionMetricIdle = "l/h"
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
                        }
                        if (metricValue == 4.0f) {
                            if (consumptionValue > 0.0f) {
                                adjustedValueIdle = kotlin.math.truncate(consumptionValue * 10) / 10
                                adjustedValue = 0.0f
                                evaluateJsIfReady (webView, "control('${GraphicsScreen.GraphOptions.GAS_CONSUMPTION_MODE}', ${GraphicsScreen.GraphOptions.GAS_CONSUMPTION_METRIC_IDLE})")
                            } else {
                                adjustedValueIdle = 0.0f
                            }
                        } else if (metricValue == 1.0f) {
                            if (consumptionValue > 0.0f) {
                                adjustedValue = kotlin.math.truncate(10 * 100 / consumptionValue) / 10
                                adjustedValueIdle = 0.0f
                                evaluateJsIfReady(
                                    webView,
                                    "control('${GraphicsScreen.GraphOptions.GAS_CONSUMPTION_MODE}', ${GraphicsScreen.GraphOptions.GAS_CONSUMPTION_METRIC_IDLE})"
                                )
                            } else {
                                adjustedValue = 0.0f
                            }
                        }
                        evaluateJsIfReady (webView, "control('${GraphicsScreen.GraphOptions.GAS_CONSUMPTION_IDLE}', $adjustedValueIdle)")
                        evaluateJsIfReady (webView, "control('${GraphicsScreen.GraphOptions.GAS_CONSUMPTION}', $adjustedValue)")
                    }


                    CarConstants.CAR_BASIC_VEHICLE_SPEED.value -> {
                        evaluateJsIfReady (webView, "control('${GraphicsScreen.GraphOptions.CAR_SPEED}',$value)")
                    }

                    else -> {}
                }
            }
        }

        ServiceManager.getInstance().addServiceManagerEventListener { event, args ->
            ensureUi {
                when (event) {
                    ServiceManagerEventType.CLUSTER_CARD_CHANGED -> {
                        val card = args[0] as Int
                        circularView.isVisible = card != 0
                        webView?.isVisible = false;
                        when (card) {
                            1 -> {
                                showWebView()
                            }

                            else -> {

                            }
                        }
                    }

                    ServiceManagerEventType.STEERING_WHEEL_AC_CONTROL -> {
                        when (args[0] as SteeringWheelAcControlType) {
                            SteeringWheelAcControlType.FAN_SPEED -> {
                                evaluateJsIfReady(webView, "focus('fan')")
                            }


                            SteeringWheelAcControlType.TEMPERATURE -> {
                                evaluateJsIfReady(webView, "focus('temp')")
                            }

                            SteeringWheelAcControlType.POWER -> {
                                evaluateJsIfReady(webView, "focus('power')")
                            }
                        }
                    }

                    ServiceManagerEventType.MENU_ITEM_NAVIGATION -> {
                        val item = args[0] as String
                        evaluateJsIfReady(webView, "focus('$item')")
                    }

                    ServiceManagerEventType.UPDATE_SCREEN -> {
                        val screen = args[0] as Screen
                        val screenName = screen.jsName
                        evaluateJsIfReady(webView, "showScreen('$screenName')")
                    }

                    ServiceManagerEventType.GRAPH_SCREEN_NAVIGATION -> {
                        val screen = args[0] as String
                        evaluateJsIfReady(webView, "control('currentGraph','$screen')")
                    }

                    ServiceManagerEventType.MAX_AUTO_AC_STATUS_CHANGED -> {
                        val maxauto = args[0] as Int
                        evaluateJsIfReady(webView, "control('maxauto', $maxauto)")
                    }

                }
            }


        }

        root.isVisible = shouldShowProjector() && ServiceManager.getInstance().isMainScreenOn
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupAcControlView(circularView: FrameLayout) {
        if (webView == null) {
            webView = WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowContentAccess = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.let {
                            webViewsLoaded[it] = true
                            updateValuesWebView()
                            val queue = pendingJsQueues[it] ?: return
                            queue.forEach { js -> it.evaluateJavascript(js, null) }
                            pendingJsQueues.remove(it)
                        }
                    }
                }
                val file = java.io.File("/data/local/tmp/app.html")

                if (file.exists()) {
                    println("🔥 Carregando HTML externo")
                    loadUrl("file:///data/local/tmp/app.html")
                    lastModified = file.lastModified()
                } else {
                    println("📦 Carregando HTML interno")
                    loadDataWithBaseURL(null, readRawHtml(context), "text/html", "UTF-8", null)
                }
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(object : Runnable {
    override fun run() {
        val file = java.io.File("/data/local/tmp/app.html")

        if (file.exists()) {
            val modified = file.lastModified()

            if (modified != lastModified) {
                lastModified = modified

                println("🔥 HTML atualizado detectado")

                this@apply.post {
                    this@apply.loadUrl("file:///data/local/tmp/app.html")
                }
            }
        }

        android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed(this, 2000)
    }
}, 2000)
            }
            circularView.addView(webView)
            webView?.isVisible = false;
        }
    }

    private fun showWebView() {
        webView?.isVisible = true
        webView?.let {
            if (webViewsLoaded[it] == true) {
                updateValuesWebView()
            }
        }
    }

    private fun updateValuesWebView() {
        val currentTemp = ServiceManager.getInstance().getData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.value)
        val currentFanSpeed = ServiceManager.getInstance().getData(CarConstants.CAR_HVAC_FAN_SPEED.value)
        val currentAcState = ServiceManager.getInstance().getData(CarConstants.CAR_HVAC_POWER_MODE.value)
        val currentRecycleMode = ServiceManager.getInstance().getData(CarConstants.CAR_HVAC_CYCLE_MODE.value)
        val currentAutoMode = ServiceManager.getInstance().getData(CarConstants.CAR_HVAC_AUTO_ENABLE.value)

        val currentEVMode = ServiceManager.getInstance().getData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.value)
        val currentDrivingMode = ServiceManager.getInstance().getData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.value)
        val currentSteerMode = ServiceManager.getInstance().getData(CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.value)

        val regenMode = ServiceManager.getInstance().getData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.value)
        val espMode = ServiceManager.getInstance().getData(CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE.value)
        val currentCarSpeed = ServiceManager.getInstance().getData(CarConstants.CAR_BASIC_VEHICLE_SPEED.value)
        val insideTemp = ServiceManager.getInstance().getData(CarConstants.CAR_BASIC_INSIDE_TEMP.value).toFloat().roundToInt()
        val outsideTemp = ServiceManager.getInstance().getData(CarConstants.CAR_BASIC_OUTSIDE_TEMP.value).toFloat().roundToInt()
        val onePedal = ServiceManager.getInstance().getData(CarConstants.CAR_CONFIGURE_PEDAL_CONTROL_ENABLE.value) == "1"

        evaluateJsIfReady(webView, "control('temp', $currentTemp)")
        evaluateJsIfReady(webView, "control('fan', $currentFanSpeed)")
        evaluateJsIfReady(webView, "control('power', $currentAcState)")
        evaluateJsIfReady(webView, "control('recycle', $currentRecycleMode)")
        evaluateJsIfReady(webView, "control('auto', $currentAutoMode)")
        evaluateJsIfReady(webView, "focus('fan')")
        evaluateJsIfReady(webView, "control('outside_temp', $outsideTemp)")
        evaluateJsIfReady(webView, "control('inside_temp', $insideTemp)")
        evaluateJsIfReady(webView, "control('onepedal', $onePedal)")
        evaluateJsIfReady(webView, "control('${GraphicsScreen.GraphOptions.CAR_SPEED}', $currentCarSpeed)")

        evaluateJsIfReady(webView, "control('evMode', ${MainMenu.EvModeOptions.getLabel(currentEVMode)})")
        evaluateJsIfReady(webView, "control('drivingMode', ${MainMenu.DrivingModeOptions.getLabel(currentDrivingMode)})")
        evaluateJsIfReady(webView, "control('steerMode', ${MainMenu.SteerModeOptions.getLabel(currentSteerMode)})")
        evaluateJsIfReady(webView, "control('espStatus', ${MainMenu.EspOptions.getLabel(espMode)})")
        evaluateJsIfReady(webView, "control('regenMode', ${RegenScreen.RegenOptions.getLabel(regenMode)})")

    }

    private fun evaluateJsIfReady(webView: WebView?, js: String) {
        if (webView == null) return
        val loaded = webViewsLoaded.getOrDefault(webView, false)
        if (loaded) {
            webView.evaluateJavascript(js, null)
        } else {
            pendingJsQueues.getOrPut(webView) { mutableListOf() }.add(js)
        }
    }

    fun readRawHtml(context: Context): String {
    return try {
        val file = java.io.File("/data/local/tmp/app.html")

        if (file.exists() && file.length() > 0) {
            println("🔥 Usando HTML externo")
            file.readText()
        } else {
            println("📦 Usando HTML interno")
            context.resources.openRawResource(R.raw.app)
                .bufferedReader()
                .use { it.readText() }
        }

    } catch (e: Exception) {
        e.printStackTrace()

        context.resources.openRawResource(R.raw.app)
            .bufferedReader()
            .use { it.readText() }
    }
}

    override fun carMainScreenOff() {
        ensureUi {
            root.isVisible = false;
        }
    }

    override fun carMainScreenOn() {
        ensureUi {
            root.isVisible = true;
        }
    }


}
