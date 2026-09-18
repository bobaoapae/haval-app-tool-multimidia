package br.com.redesurftank.havalshisuku.diagnostics

import android.content.Context
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.BuildConfig
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import com.google.gson.Gson
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * One anonymous fleet ping per power-on (with 5-minute debounce). Disabled when
 * [BuildConfig.POSTHOG_API_KEY] is blank or the user opted out on Informações.
 */
object AnonymousTelemetryCollector {
    private const val TAG = "AnonTelemetry"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000
    private val gson = Gson()

    private val settingKeys =
            listOf(
                    SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR to "enable_instrument_projector",
                    SharedPreferencesKeys.ENABLE_CUSTOM_MENU to "enable_custom_menu",
                    SharedPreferencesKeys.ENABLE_AA_CLUSTER_OFFSET to "enable_aa_cluster_offset",
                    SharedPreferencesKeys.CARPLAY_PATCH_AUTO_MOUNT to "carplay_patch_auto_mount",
                    SharedPreferencesKeys.ENABLE_AUTO_BRIGHTNESS to "enable_auto_brightness",
                    SharedPreferencesKeys.ENABLE_SPEED_ADJUSTMENT to "enable_speed_adjustment",
                    SharedPreferencesKeys.ENABLE_HOT_ROUTER to "enable_hot_router",
                    SharedPreferencesKeys.DISABLE_AVAS to "disable_avas",
                    SharedPreferencesKeys.DISABLE_NATIVE_NAVIGATION to "disable_native_navigation",
                    SharedPreferencesKeys.DISABLE_NATIVE_VOICE to "disable_native_voice",
                    SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS to
                            "enable_steering_wheel_custom_buttons",
                    SharedPreferencesKeys.CLOSE_WINDOW_ON_POWER_OFF to "close_window_on_power_off",
                    SharedPreferencesKeys.CLOSE_SUNROOF_ON_POWER_OFF to "close_sunroof_on_power_off",
            )

    fun isConfigured(): Boolean = BuildConfig.POSTHOG_API_KEY.isNotBlank()

    fun maybePingAfterServicesReady(context: Context = App.getDeviceProtectedContext()) {
        if (!isConfigured()) {
            Log.d(TAG, "skipped: PostHog API key not configured")
            return
        }

        val prefs = context.getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean(SharedPreferencesKeys.ANONYMOUS_TELEMETRY_OPTED_OUT.key, false)) {
            Log.d(TAG, "skipped: user opted out")
            return
        }

        val now = System.currentTimeMillis()
        val lastSent =
                prefs.getLong(SharedPreferencesKeys.ANONYMOUS_TELEMETRY_LAST_SENT_AT.key, 0L)
        if (AnonymousTelemetryPayload.shouldDebounce(lastSent, now)) {
            Log.d(TAG, "skipped: debounce window")
            return
        }

        val vin =
                ServiceManager.getInstance()
                        .getData(CarConstants.CAR_BASIC_VIN_CODE.getValue())
                        ?.trim()
                        .orEmpty()
        if (vin.isEmpty()) {
            Log.d(TAG, "skipped: VIN unavailable")
            return
        }

        val salt = BuildConfig.TELEMETRY_VIN_SALT
        val vehicleUuid = AnonymousTelemetryPayload.vehicleUuid(vin, salt)
        val theme =
                prefs.getString(SharedPreferencesKeys.VIRTUAL_CLUSTER_THEME.key, "Default")
                        ?: "Default"
        val lastTheme =
                prefs.getString(SharedPreferencesKeys.ANONYMOUS_TELEMETRY_LAST_THEME.key, null)
        val nextPowerOn =
                prefs.getLong(SharedPreferencesKeys.ANONYMOUS_TELEMETRY_POWER_ON_COUNT.key, 0L) + 1L

        val settings =
                settingKeys.associate { (prefKey, propName) ->
                    propName to prefs.getBoolean(prefKey.key, false)
                }

        val properties =
                AnonymousTelemetryPayload.buildProperties(
                        vehicleUuid = vehicleUuid,
                        vinPrefix = AnonymousTelemetryPayload.vinPrefix(vin),
                        vehicleModel1 =
                                ServiceManager.getInstance()
                                        .getData(CarConstants.CAR_BASIC_VEHICLE_MODEL1.getValue()),
                        vehicleModel2 =
                                ServiceManager.getInstance()
                                        .getData(CarConstants.CAR_BASIC_VEHICLE_MODEL2.getValue()),
                        configureCode = readProp("persist.bean.configure.code"),
                        trimLevel = readProp("persist.vendor.gwm.cfg.trim.level"),
                        carMode1 = readProp("persist.bean.car.mode1"),
                        engineType = readProp("persist.bean.engine.type"),
                        projectName = readProp("ro.bean.project.name"),
                        odometerKmBucket =
                                AnonymousTelemetryPayload.odometerBucketKm(
                                        ServiceManager.getInstance()
                                                .getData(
                                                        CarConstants.CAR_BASIC_TOTAL_ODOMETER
                                                                .getValue()
                                                )
                                ),
                        theme = theme,
                        themeChanged = AnonymousTelemetryPayload.themeChanged(theme, lastTheme),
                        powerOnCount = nextPowerOn,
                        virtualClusterEnabled =
                                prefs.getBoolean(
                                        SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key,
                                        false
                                ),
                        settings = settings,
                        appVersion = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE
                )

        val payload =
                mapOf(
                        "api_key" to BuildConfig.POSTHOG_API_KEY,
                        "event" to AnonymousTelemetryPayload.EVENT_NAME,
                        "distinct_id" to vehicleUuid,
                        "properties" to properties
                )

        when (val result = postEvent(payload)) {
            is PostResult.Success -> {
                prefs.edit()
                        .putLong(SharedPreferencesKeys.ANONYMOUS_TELEMETRY_LAST_SENT_AT.key, now)
                        .putString(SharedPreferencesKeys.ANONYMOUS_TELEMETRY_LAST_THEME.key, theme)
                        .putLong(
                                SharedPreferencesKeys.ANONYMOUS_TELEMETRY_POWER_ON_COUNT.key,
                                nextPowerOn
                        )
                        .apply()
                Log.i(TAG, "ping ok power_on_count=$nextPowerOn theme=$theme")
            }
            is PostResult.Failure -> Log.w(TAG, "ping failed: ${result.message}")
        }
    }

    private fun readProp(name: String): String? {
        return try {
            val out = ShizukuUtils.runCommandAndGetOutput(arrayOf("getprop", name)).trim()
            out.ifBlank { null }
        } catch (t: Throwable) {
            Log.d(TAG, "getprop $name failed: ${t.message}")
            null
        }
    }

    private fun postEvent(payload: Map<String, Any?>): PostResult {
        val body = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        var connection: HttpURLConnection? = null
        return try {
            val host = BuildConfig.POSTHOG_HOST.trimEnd('/')
            connection = (URL("$host/i/v0/e/").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                useCaches = false
                setFixedLengthStreamingMode(body.size)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty(
                        "User-Agent",
                        "ImpulseAnonTelemetry/${BuildConfig.VERSION_NAME} Android"
                )
                setRequestProperty("Connection", "close")
            }
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            if (code in 200..299) {
                PostResult.Success
            } else {
                val err =
                        runCatching {
                                    connection.errorStream
                                            ?.bufferedReader(Charsets.UTF_8)
                                            ?.use { it.readText() }
                                            .orEmpty()
                                }
                                .getOrDefault("")
                PostResult.Failure("HTTP $code ${err.take(120)}")
            }
        } catch (e: IOException) {
            PostResult.Failure(e.message ?: "network error")
        } catch (e: Exception) {
            PostResult.Failure(e.message ?: "send error")
        } finally {
            connection?.disconnect()
        }
    }

    private sealed class PostResult {
        data object Success : PostResult()
        data class Failure(val message: String) : PostResult()
    }
}
