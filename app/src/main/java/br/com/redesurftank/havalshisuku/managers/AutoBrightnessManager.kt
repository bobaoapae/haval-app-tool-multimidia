package br.com.redesurftank.havalshisuku.managers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.location.Geocoder
import android.location.LocationManager
import android.util.Log
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.broadcastReceivers.AutoBrightnessReceiver
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.utils.BrightnessSchedule
import br.com.redesurftank.havalshisuku.utils.SolarCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoBrightnessManager private constructor() {
    companion object {
        private const val TAG = "AutoBrightnessManager"
        private const val REQ_START = 0
        private const val REQ_END = 1
        private const val REQ_SUN_TICK = 2

        @Volatile
        private var INSTANCE: AutoBrightnessManager? = null
        fun getInstance(): AutoBrightnessManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutoBrightnessManager().also { INSTANCE = it }
            }
        }
    }

    private val prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    private val alarmManager = App.getContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun setEnabled(enabled: Boolean) {
        if (enabled) updateSchedule() else cancelSchedules()
    }

    private fun useSun(): Boolean =
        prefs.getBoolean(SharedPreferencesKeys.AUTO_BRIGHTNESS_USE_SUN.key, false)

    private fun dayLevel() = prefs.getInt(SharedPreferencesKeys.AUTO_BRIGHTNESS_LEVEL_DAY.key, 10)
    private fun nightLevel() = prefs.getInt(SharedPreferencesKeys.AUTO_BRIGHTNESS_LEVEL_NIGHT.key, 1)
    private fun stepMs(): Long =
        prefs.getInt(SharedPreferencesKeys.AUTO_BRIGHTNESS_STEP_MIN.key, BrightnessSchedule.DEFAULT_STEP_MIN)
            .coerceIn(BrightnessSchedule.MIN_STEP_MIN, BrightnessSchedule.MAX_STEP_MIN) * 60_000L

    fun updateSchedule() {
        cancelSchedules()
        if (useSun() && applySunSchedule()) {
            return // modo sol aplicou e agendou o próximo tick
        }
        // Modo horário fixo (ou fallback quando não há localização/sol).
        if (isNightTime()) adjustBrightnessForNight() else adjustBrightnessForDay()
        scheduleNextStart()
        scheduleNextEnd()
    }

    // ---------------- MODO NASCER/PÔR DO SOL ----------------

    /** Aplica o brilho por posição do sol e agenda o próximo tick. Retorna false se não deu (sem
     *  localização nem cache, ou dia/noite polar) -> o chamador cai no modo horário fixo. */
    private fun applySunSchedule(): Boolean {
        val loc = resolveLatLon() ?: return false
        val (lat, lon) = loc
        val now = System.currentTimeMillis()
        val sun = SolarCalculator.calculate(lat, lon, now) ?: return false

        val day = dayLevel()
        val night = nightLevel()
        val step = stepMs()
        val result = BrightnessSchedule.levelFor(now, sun.sunriseUtcMs, sun.sunsetUtcMs, day, night, step)
        setBrightness(result.level)

        val nextAt =
            if (result.inTransition) {
                now + step
            } else {
                val half = BrightnessSchedule.totalMs(day, night, step) / 2
                val riseStart = sun.sunriseUtcMs - half
                val setStart = sun.sunsetUtcMs - half
                when {
                    now < riseStart -> riseStart
                    now < setStart -> setStart
                    else -> {
                        val tomorrow = SolarCalculator.calculate(lat, lon, now + 86_400_000L)
                        ((tomorrow?.sunriseUtcMs ?: (sun.sunriseUtcMs + 86_400_000L)) - half)
                    }
                }
            }
        scheduleSunTick(nextAt.coerceAtLeast(now + 30_000L)) // nunca menos de 30s (evita loop)
        Log.w(TAG, "sun brightness level=${result.level} transition=${result.inTransition} next=${Date(nextAt)}")
        return true
    }

    /** Localização atual: tenta os providers (GPS/passive/network/fused), cacheia; senão usa o cache. */
    private fun resolveLatLon(): Pair<Double, Double>? {
        try {
            val lm = App.getContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                "fused"
            )
            val best = providers
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            if (best != null) {
                cacheLatLon(best.latitude, best.longitude)
                return best.latitude to best.longitude
            }
        } catch (e: Exception) {
            Log.w(TAG, "sem localização ao vivo: ${e.message}")
        }
        // Fallback: último válido salvo (funciona offline/garagem).
        val lat = prefs.getString(SharedPreferencesKeys.AUTO_BRIGHTNESS_LAST_LAT.key, null)?.toDoubleOrNull()
        val lon = prefs.getString(SharedPreferencesKeys.AUTO_BRIGHTNESS_LAST_LON.key, null)?.toDoubleOrNull()
        return if (lat != null && lon != null) lat to lon else null
    }

    private fun cacheLatLon(lat: Double, lon: Double) {
        prefs.edit()
            .putString(SharedPreferencesKeys.AUTO_BRIGHTNESS_LAST_LAT.key, lat.toString())
            .putString(SharedPreferencesKeys.AUTO_BRIGHTNESS_LAST_LON.key, lon.toString())
            .apply()
        // Geocode best-effort EM BACKGROUND (getFromLocation usa rede e pode bloquear vários segundos;
        // roda em main thread do receiver/UI daria ANR). Atualiza a cidade cacheada quando resolver.
        if (!Geocoder.isPresent()) return
        Thread {
            runCatching {
                @Suppress("DEPRECATION")
                val results = Geocoder(App.getContext(), Locale("pt", "BR")).getFromLocation(lat, lon, 1)
                val addr = results?.firstOrNull()
                val city = addr?.subAdminArea ?: addr?.locality ?: addr?.adminArea
                if (!city.isNullOrBlank()) {
                    prefs.edit().putString(SharedPreferencesKeys.AUTO_BRIGHTNESS_CITY.key, city).apply()
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun scheduleSunTick(at: Long) {
        val intent = Intent(App.getContext(), AutoBrightnessReceiver::class.java).apply {
            putExtra("sunTick", true)
        }
        val pi = PendingIntent.getBroadcast(
            App.getContext(), REQ_SUN_TICK, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** Info pra UI: cidade de referência + nascer/pôr do sol de hoje (horário local). Null se nunca
     *  houve localização. Usa o último lat/lon salvo -> funciona mesmo sem GPS no momento. */
    // cityResolved=false quando ainda é o fallback de coordenadas (geocode não resolveu) — a UI
    // usa isso pra continuar tentando até virar o nome amigável da cidade.
    data class SunDisplayInfo(
        val city: String,
        val sunrise: String,
        val sunset: String,
        val cityResolved: Boolean
    )

    fun getSunInfoForDisplay(): SunDisplayInfo? {
        val loc = resolveLatLon() ?: return null
        val (lat, lon) = loc
        val sun = SolarCalculator.calculate(lat, lon, System.currentTimeMillis()) ?: return null
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val cached = prefs.getString(SharedPreferencesKeys.AUTO_BRIGHTNESS_CITY.key, null)
        val city = cached ?: String.format(Locale.US, "%.3f, %.3f", lat, lon)
        return SunDisplayInfo(
            city,
            fmt.format(Date(sun.sunriseUtcMs)),
            fmt.format(Date(sun.sunsetUtcMs)),
            cityResolved = !cached.isNullOrBlank()
        )
    }

    private fun setBrightness(level: Int) {
        ServiceManager.getInstance().executeWithServicesRunning {
            val v = level.toString()
            ServiceManager.getInstance().updateData(CarConstants.SYS_SETTINGS_DISPLAY_BRIGHTNESS_LEVEL.value, v)
            ServiceManager.getInstance().updateData(CarConstants.CAR_IPK_SETTING_BRIGHTNESS_CONFIG.value, v)
        }
    }

    // ---------------- MODO HORÁRIO FIXO (original) ----------------

    private fun isNightTime(): Boolean {
        val now = Calendar.getInstance()
        val currentTime = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startHour = prefs.getInt(SharedPreferencesKeys.NIGHT_START_HOUR.key, 20)
        val startMin = prefs.getInt(SharedPreferencesKeys.NIGHT_START_MINUTE.key, 0)
        val startTime = startHour * 60 + startMin
        val endHour = prefs.getInt(SharedPreferencesKeys.NIGHT_END_HOUR.key, 6)
        val endMin = prefs.getInt(SharedPreferencesKeys.NIGHT_END_MINUTE.key, 0)
        val endTime = endHour * 60 + endMin
        return if (startTime < endTime) {
            currentTime >= startTime && currentTime < endTime
        } else {
            currentTime >= startTime || currentTime < endTime
        }
    }

    fun adjustBrightnessForNight() = setBrightness(nightLevel())

    fun adjustBrightnessForDay() = setBrightness(dayLevel())

    private fun scheduleNextStart() {
        val calendar = Calendar.getInstance()
        val startHour = prefs.getInt(SharedPreferencesKeys.NIGHT_START_HOUR.key, 20)
        val startMin = prefs.getInt(SharedPreferencesKeys.NIGHT_START_MINUTE.key, 0)
        calendar.set(Calendar.HOUR_OF_DAY, startHour)
        calendar.set(Calendar.MINUTE, startMin)
        calendar.set(Calendar.SECOND, 0)
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(App.getContext(), AutoBrightnessReceiver::class.java).apply {
            putExtra("isNight", true)
        }
        val pendingIntent = PendingIntent.getBroadcast(App.getContext(), REQ_START, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
    }

    private fun scheduleNextEnd() {
        val calendar = Calendar.getInstance()
        val endHour = prefs.getInt(SharedPreferencesKeys.NIGHT_END_HOUR.key, 6)
        val endMin = prefs.getInt(SharedPreferencesKeys.NIGHT_END_MINUTE.key, 0)
        calendar.set(Calendar.HOUR_OF_DAY, endHour)
        calendar.set(Calendar.MINUTE, endMin)
        calendar.set(Calendar.SECOND, 0)
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(App.getContext(), AutoBrightnessReceiver::class.java).apply {
            putExtra("isNight", false)
        }
        val pendingIntent = PendingIntent.getBroadcast(App.getContext(), REQ_END, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
    }

    private fun cancelSchedules() {
        for (req in listOf(REQ_START, REQ_END, REQ_SUN_TICK)) {
            val intent = Intent(App.getContext(), AutoBrightnessReceiver::class.java)
            val pi = PendingIntent.getBroadcast(App.getContext(), req, intent, PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(pi)
        }
    }
}
