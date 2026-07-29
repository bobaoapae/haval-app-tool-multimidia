package br.com.redesurftank.havalshisuku.bridge

import android.webkit.JavascriptInterface
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ThemeBridgeImpl(private val context: IBridgeContext) {
    private val TAG = "ThemeBridgeImpl"

    @JavascriptInterface
    fun heartbeat() {
        context.updateHeartbeat()
    }

    @JavascriptInterface
    fun setWarningActive(isActive: Boolean) {
        context.updateWarningUI(isActive)
    }

    @JavascriptInterface
    fun setAppDefaultDimensions(x: Int, y: Int, width: Int, height: Int) {
        Log.d(TAG, "setAppDefaultDimensions: x=$x y=$y width=$width height=$height")
        DisplayAppLauncher.dynamicThemeBounds = intArrayOf(x, y, width, height)
        context.refreshDisplayBounds()
    }

    @JavascriptInterface
    fun setCardId(cardId: Int) {
        context.setCardId(cardId)
    }

    @JavascriptInterface
    fun saveSetting(key: String, value: String) {
        context.saveClusterDisplay(value)
    }

    @JavascriptInterface
    fun updateCarData(key: String, value: String) {
        ServiceManager.getInstance().updateData(key, value)
    }

    @JavascriptInterface
    fun triggerSystemAction(action: String) {
        triggerSystemAction(action, "")
    }

    @JavascriptInterface
    fun triggerSystemAction(action: String, payload: String) {
        Log.d(TAG, "triggerSystemAction action=$action payload=$payload")
        when (action) {
            "CANCEL_MAX_AC" -> ServiceManager.getInstance().cancelMaxAcMode()
            "TRIGGER_AVM_CAMERA" -> {
                ServiceManager.getInstance().handleSteeringWheelCustomButton(
                    br.com.redesurftank.havalshisuku.models.SteeringWheelCustomActionType.OPEN_AVM_ONCE.name,
                    1
                )
            }
            "DISMISS_WARNINGS" -> {
                context.updateWarningUI(false)
            }
            "BRING_ALL_TO_MAIN" -> {
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        DisplayAppLauncher.bringAllToMainDisplay()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed BRING_ALL_TO_MAIN", e)
                    }
                }
            }
            "MEDIA_CONTROL" -> {
                Log.i(TAG, "Media action: $payload")
            }
            "PHONE_CONTROL" -> {
                Log.i(TAG, "Phone action: $payload")
            }
            else -> {
                ServiceManager.getInstance().handleSteeringWheelCustomButton(action, 1)
            }
        }
    }

    @JavascriptInterface
    fun savePreference(key: String, value: String) {
        context.preferences.edit().putString(key, value).apply()
    }

    /**
     * Theme → native bridge: set the Display-1 cluster wallpaper.
     *
     * Types:
     *  - "THEME"      value = relative path inside the active theme package (e.g. "car-bg.png");
     *                 empty value uses `<background>` from theme.xml
     *  - "PRESET"     value = asset filename under assets/backgrounds/
     *  - "IMAGE_URL"  value = http(s) URL
     *  - "FILE"       value = absolute filesystem path
     *  - "WEB_URL"    value = page URL rendered in a WebView
     *
     * Enables custom background automatically. Safe no-op when type is blank.
     */
    @JavascriptInterface
    fun setClusterBackground(type: String, value: String) {
        val normalizedType = type.trim().uppercase().ifBlank { "THEME" }
        val normalizedValue = value.trim()
        Log.d(TAG, "setClusterBackground type=$normalizedType value=$normalizedValue")
        try {
            context.preferences.edit()
                .putBoolean(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true)
                .putString(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, normalizedType)
                .putString(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, normalizedValue)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "setClusterBackground failed", e)
        }
    }

    /**
     * Convenience: announce the theme package's wallpaper path without changing
     * the user's selected mode. Only applies when mode is THEME (or unset).
     */
    @JavascriptInterface
    fun setThemeBackground(relativePath: String) {
        val path = relativePath.trim()
        if (path.isEmpty()) return
        Log.d(TAG, "setThemeBackground path=$path")
        try {
            val prefs = context.preferences
            val currentType = prefs.getString(
                br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key,
                "THEME"
            ) ?: "THEME"
            // Always remember the theme-declared path as value when in THEME mode
            if (currentType.equals("THEME", ignoreCase = true)) {
                prefs.edit()
                    .putBoolean(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.ENABLE_CUSTOM_BACKGROUND_D1.key, true)
                    .putString(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.CUSTOM_BACKGROUND_TYPE_D1.key, "THEME")
                    .putString(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.CUSTOM_BACKGROUND_VALUE_D1.key, path)
                    .apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "setThemeBackground failed", e)
        }
    }

    @JavascriptInterface
    fun getPreference(key: String, defaultValue: String): String {
        // Must match the identifier ThemeSettingsDialog scopes its saves with
        // (theme.folderName, e.g. "minimalist"), NOT VIRTUAL_CLUSTER_THEME
        // (theme.name display label, e.g. "Minimalist") — those differ in case
        // for every non-Default theme, so scoped config lookups always missed.
        val activeCustomTheme = context.preferences.getString(br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.key, "") ?: ""
        val activeTheme = activeCustomTheme.ifBlank { "Default" }
        val scopedKey = "theme_config_${activeTheme}_$key"

        // Transparent theme-scoped lookup fallback
        if (context.preferences.contains(scopedKey)) {
            val result = context.preferences.getString(scopedKey, defaultValue) ?: defaultValue
            Log.w(TAG, "getPreference: key=$key scopedKey=$scopedKey (HIT) -> $result")
            return result
        }
        Log.w(TAG, "getPreference: key=$key scopedKey=$scopedKey (MISS, activeCustomTheme='$activeCustomTheme') -> unscoped fallback")
        return context.preferences.getString(key, defaultValue) ?: defaultValue
    }

    @JavascriptInterface
    fun subscribe(keysJson: String) {
        try {
            val jsonArray = JSONArray(keysJson)
            val keysToMonitor = mutableListOf<String>()
            val contextApp = App.getContext()
            for (i in 0 until jsonArray.length()) {
                val rawKey = jsonArray.getString(i)
                context.subscribedKeys.add(rawKey)
                
                val canonicalKey = BridgeContractTranslator.translateThemeKeyToCanonical(rawKey)

                if (canonicalKey.startsWith("app.preferences.")) {
                    // Initial value already delivered via getPreference() at theme init
                    // (see ThemeBridgeAdapter.bindThemeSetting). VirtualTelemetryManager has
                    // no notion of scoped preferences, so pushing its value here (always "")
                    // would clobber the correct value with an empty string right after load —
                    // this is only registering for future PreferencePushListener pushes.
                } else if (canonicalKey.startsWith("app.")) {
                    val virtualVal = VirtualTelemetryManager.getVirtualValue(contextApp, canonicalKey)
                    pushValueToTheme(rawKey, virtualVal)
                } else {
                    keysToMonitor.add(canonicalKey)
                    val currentValue = ServiceManager.getInstance().getData(canonicalKey)
                    if (currentValue != null) {
                        pushValueToTheme(rawKey, currentValue)
                    }
                }
            }
            if (keysToMonitor.isNotEmpty()) {
                ServiceManager.getInstance().ensureKeysMonitored(keysToMonitor)
            }
            Log.d(TAG, "Subscribed to keys: $keysJson")
        } catch (e: Exception) {
            Log.e(TAG, "subscribe failed: $keysJson", e)
        }
    }

    @JavascriptInterface
    fun unsubscribe(keysJson: String) {
        try {
            val jsonArray = JSONArray(keysJson)
            for (i in 0 until jsonArray.length()) {
                val key = jsonArray.getString(i)
                context.subscribedKeys.remove(key)
            }
            Log.d(TAG, "Unsubscribed from keys: $keysJson")
        } catch (e: Exception) {
            Log.e(TAG, "unsubscribe failed: $keysJson", e)
        }
    }

    @JavascriptInterface
    fun getCarData(key: String): String {
        val canonicalKey = BridgeContractTranslator.translateThemeKeyToCanonical(key)
        if (canonicalKey.startsWith("app.")) {
            return VirtualTelemetryManager.getVirtualValue(App.getContext(), canonicalKey)
        }
        val valStr = ServiceManager.getInstance().getData(canonicalKey)
        return valStr ?: ""
    }

    @JavascriptInterface
    fun launchApp(packageName: String, displayId: Int) {
        Log.d(TAG, "launchApp: packageName=$packageName displayId=$displayId")
        val config = DisplayAppLauncher.getOrCreateDefaultConfig(App.getContext(), packageName, false) ?: br.com.redesurftank.havalshisuku.models.DisplayAppConfig(
            packageName = packageName,
            activityName = "",
            displayId = displayId,
            x = 0, y = 0, width = 1920, height = 720
        )
        val targetConfig = config.copy(displayId = displayId)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (displayId == 0) {
                    DisplayAppLauncher.launchAnyApp(App.getContext(), packageName)
                } else {
                    DisplayAppLauncher.sendToDisplay(targetConfig)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch app $packageName on display $displayId", e)
            }
        }
    }

    @JavascriptInterface
    fun killApp(packageName: String) {
        Log.d(TAG, "killApp: packageName=$packageName")
        DisplayAppLauncher.killAppAsync(packageName)
    }

    @JavascriptInterface
    fun getAvailableKeys(): String {
        val keys = listOf(
            "car.basic.vehicle_speed",
            "car.basic.engine_speed",
            "car.basic.instant_fuel_consumption",
            "car.basic.total_odometer",
            "car.basic.gear_status",
            "car.basic.inside_temp",
            "car.basic.outside_temp",
            "car.basic.remain_fuel_percentage",
            "car.ev_info.cur_battery_power_percentage",
            "car.ev_info.fuel_mode_remain_odometer",
            "car.ev_info.electric_mode_remain_odometer",
            "car.configure.default_temp_unit",
            // Drive/EV settings: already in ServiceManager.DEFAULT_KEYS (continuously
            // monitored), but were missing here, so ThemeBridgeAdapter.hasKey() silently
            // rejected any theme's Android.subscribe() call for them — the values only
            // ever reached themes via a one-shot card-entry snapshot push, never live.
            "car.drive_setting.esp_enable",
            "car.ev_setting.power_model_config",
            "car.drive_setting.drive_mode",
            "car.drive_setting.steering_wheel_assist_mode",
            "car.ev_setting.energy_recovery_level",
            "car.ev.setting.pedal_control_enable",
            "car.ev_info.energy_output_percentage",
            "car.ev_info.cur_charge_current",
            "car.ev_info.power_battery_voltage",
            "car.ev_info.Instant_energy_consumption",
            "car.hvac.power_mode",
            "car.hvac.fan_speed",
            "car.hvac.driver_temperature",
            "car.hvac.cycle_mode",
            "car.hvac.auto_enable",
            "car.hvac.anion_enable",
            "app.display.1.active_app",
            "app.display.1.active_app_label",
            "app.display.1.active_app_icon",
            "app.display.3.active_app",
            "app.display.3.active_app_label",
            "app.display.3.active_app_icon",
            "app.launcher.apps",
            "app.navigation.directions",
            "app.media.state",
            "app.media.title",
            "app.media.artist",
            "app.media.album",
            "app.media.duration",
            "app.media.position",
            "app.media.album_art",
            "app.phone.state",
            "app.phone.caller_name",
            "app.phone.caller_number",
            "app.phone.call_duration",
            "bsdLeft",
            "bsdRight",
            "carPlayInDash",
            "projectionMirrorInDash",
            "projectionPreparingD3",
            "projectionCardOverlayAllowed"
        )
        return JSONArray(keys).toString()
    }

    fun pushOnDataChanged(key: String, value: String) {
        val quotedValue = JSONObject.quote(value)
        val webView = context.webView ?: return
        Log.d(TAG, "[DIAG] pushOnDataChanged: key=$key value=$value")
        context.runOnUiThread {
            webView.evaluateJavascript("javascript:if (window.onDataChanged) window.onDataChanged('$key', $quotedValue);", null)
        }
    }

    private fun pushValueToTheme(key: String, value: String) {
        pushOnDataChanged(key, value)
    }
}
