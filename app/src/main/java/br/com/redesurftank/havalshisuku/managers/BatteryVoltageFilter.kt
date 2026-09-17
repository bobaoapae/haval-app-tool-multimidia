package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.CarConstants
import java.util.Locale

/**
 * Normalizes and filters telemetry updates for high-frequency or noisy sensors.
 *
 * For 12V battery voltage ([CarConstants.CAR_BASIC_BATTERY_VOLTAGE]), micro-fluctuations in the
 * 2nd decimal place (ADC noise / alternator ripple) are rounded to 1 decimal place (e.g. 14.23 -> 14.2)
 * so that back-to-back CAN messages with identical 1st decimals do not flood Android IPC broadcasts,
 * in-memory listeners, and cluster WebView bridge evaluateJavascript calls.
 */
object BatteryVoltageFilter {

    @JvmStatic
    fun normalize(key: String, value: String?): String? {
        if (value == null) return null
        if (key == CarConstants.CAR_BASIC_BATTERY_VOLTAGE.value) {
            try {
                val voltage = value.trim().replace(',', '.').toFloat()
                if (!voltage.isNaN() && !voltage.isInfinite()) {
                    return String.format(Locale.US, "%.1f", voltage)
                }
            } catch (_: NumberFormatException) {
                // Return raw string if not parseable as float
            }
        }
        return value
    }

    @JvmStatic
    fun shouldSuppress(key: String, normalizedValue: String?, cachedValue: String?): Boolean {
        if (key == CarConstants.CAR_BASIC_BATTERY_VOLTAGE.value && normalizedValue != null) {
            return normalizedValue == cachedValue
        }
        return false
    }
}
