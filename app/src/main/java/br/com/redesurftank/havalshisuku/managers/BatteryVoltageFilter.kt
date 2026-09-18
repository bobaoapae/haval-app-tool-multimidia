package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.CarConstants
import java.util.Locale

import kotlin.math.roundToInt

/**
 * Normalizes and filters telemetry updates for high-frequency or noisy sensors.
 *
 * - For 12V battery voltage ([CarConstants.CAR_BASIC_BATTERY_VOLTAGE]), micro-fluctuations in the
 *   2nd decimal place (ADC noise / alternator ripple) are rounded to 1 decimal place (e.g. 14.23 -> 14.2).
 * - For steering wheel angle ([CarConstants.CAR_BASIC_STEERING_WHEEL_ANGLE]), high-frequency SAS sensor
 *   jitter (20-50 Hz) is rounded to integer degrees (e.g. 14.32° -> 14°).
 *
 * Back-to-back events with identical normalized values are suppressed, and metrics are periodically
 * flushed to Logcat every 10 minutes.
 */
object BatteryVoltageFilter {

    private const val TAG = "TelemetryFilter"
    private const val DEFAULT_LOG_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes

    internal class SuppressionTracker(
        private val windowMs: Long = DEFAULT_LOG_INTERVAL_MS,
        private val clock: () -> Long = { System.currentTimeMillis() },
        private val logger: (String) -> Unit = { msg ->
            try {
                android.util.Log.i(TAG, msg)
            } catch (_: Throwable) {
                println("[$TAG] $msg")
            }
        }
    ) {
        private val suppressedTotal = java.util.concurrent.atomic.AtomicLong(0L)
        private val suppressedPerKey = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()
        private val lastLogMs = java.util.concurrent.atomic.AtomicLong(clock())

        fun recordSuppressed(key: String) {
            suppressedTotal.incrementAndGet()
            suppressedPerKey.computeIfAbsent(key) { java.util.concurrent.atomic.AtomicLong(0L) }.incrementAndGet()

            val now = clock()
            val prev = lastLogMs.get()
            if (now - prev >= windowMs) {
                if (lastLogMs.compareAndSet(prev, now)) {
                    flush(now - prev)
                }
            }
        }

        fun getSuppressedCount(key: String): Long = suppressedPerKey[key]?.get() ?: 0L

        fun getTotalSuppressed(): Long = suppressedTotal.get()

        internal fun flush(elapsedMs: Long) {
            val total = suppressedTotal.getAndSet(0L)
            if (total == 0L) return
            val breakdown = StringBuilder()
            for ((k, counter) in suppressedPerKey) {
                val count = counter.getAndSet(0L)
                if (count > 0) {
                    if (breakdown.isNotEmpty()) breakdown.append(", ")
                    breakdown.append(k).append(": ").append(count)
                }
            }
            val minutes = elapsedMs / 60000.0
            logger("Suppressed $total telemetry events in the last ${String.format(Locale.US, "%.1f", minutes)} min ($breakdown)")
        }
    }

    internal var tracker = SuppressionTracker()

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
        if (key == CarConstants.CAR_BASIC_STEERING_WHEEL_ANGLE.value) {
            try {
                val angle = value.trim().replace(',', '.').toFloat()
                if (!angle.isNaN() && !angle.isInfinite()) {
                    return angle.roundToInt().toString()
                }
            } catch (_: NumberFormatException) {
                // Return raw string if not parseable as float
            }
        }
        return value
    }

    @JvmStatic
    fun shouldSuppress(key: String, normalizedValue: String?, cachedValue: String?): Boolean {
        val suppress = when (key) {
            CarConstants.CAR_BASIC_BATTERY_VOLTAGE.value -> normalizedValue != null && normalizedValue == cachedValue
            CarConstants.CAR_BASIC_STEERING_WHEEL_ANGLE.value -> normalizedValue != null && normalizedValue == cachedValue
            else -> false
        }
        if (suppress) {
            tracker.recordSuppressed(key)
        }
        return suppress
    }
}
