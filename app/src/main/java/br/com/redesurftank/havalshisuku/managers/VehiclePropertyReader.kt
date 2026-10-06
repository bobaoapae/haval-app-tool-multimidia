package br.com.redesurftank.havalshisuku.managers

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.SystemClock
import rikka.shizuku.ShizukuBinderWrapper

/**
 * Best-effort reader for the hidden car property service.
 *
 * The OEM binder is always wrapped through Shizuku before a hidden interface is created. Missing
 * services, unsupported firmware and binder failures are intentionally represented as `null`; the
 * cluster must never manufacture a vehicle value when the source cannot be proven.
 */
@SuppressLint("PrivateApi")
internal class VehiclePropertyReader {
    private var propertyService: Any? = null
    private var getPropertyMethod: java.lang.reflect.Method? = null
    private var getValueMethod: java.lang.reflect.Method? = null
    private var lastInitializationAttemptAtMs = Long.MIN_VALUE

    @Synchronized
    fun readPropertyAsString(propertyId: Int, areaId: Int = 0): String? {
        ensurePropertyService()
        val service = propertyService ?: return null
        val getProperty = getPropertyMethod ?: return null
        val getValue = getValueMethod ?: return null

        return try {
            val carPropertyValue = getProperty.invoke(service, propertyId, areaId) ?: return null
            getValue.invoke(carPropertyValue)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            clearCachedService()
            null
        }
    }

    @Synchronized
    fun close() {
        clearCachedService()
        lastInitializationAttemptAtMs = Long.MIN_VALUE
    }

    private fun ensurePropertyService() {
        if (propertyService != null) return

        val now = SystemClock.uptimeMillis()
        if (
                lastInitializationAttemptAtMs != Long.MIN_VALUE &&
                        now - lastInitializationAttemptAtMs < RETRY_INTERVAL_MS
        ) {
            return
        }
        lastInitializationAttemptAtMs = now

        try {
            val rawCarBinder =
                    Class.forName("android.os.ServiceManager")
                            .getMethod("getService", String::class.java)
                            .invoke(null, "car_service") as? IBinder
                            ?: return
            if (!rawCarBinder.pingBinder()) return

            val carInterface =
                    Class.forName("android.car.ICar\$Stub")
                            .getMethod("asInterface", IBinder::class.java)
                            .invoke(null, ShizukuBinderWrapper(rawCarBinder))
                            ?: return
            val rawPropertyBinder =
                    carInterface.javaClass
                            .getMethod("getCarService", String::class.java)
                            .invoke(carInterface, "property") as? IBinder
                            ?: return
            if (!rawPropertyBinder.pingBinder()) return

            val candidateService =
                    Class.forName("android.car.hardware.property.ICarProperty\$Stub")
                            .getMethod("asInterface", IBinder::class.java)
                            .invoke(null, ShizukuBinderWrapper(rawPropertyBinder))
                            ?: return
            val candidateGetProperty =
                    candidateService.javaClass.getMethod(
                            "getProperty",
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType
                    )
            val candidateGetValue =
                    Class.forName("android.car.hardware.CarPropertyValue").getMethod("getValue")

            getPropertyMethod = candidateGetProperty
            getValueMethod = candidateGetValue
            propertyService = candidateService
        } catch (_: Throwable) {
            clearCachedService()
        }
    }

    private fun clearCachedService() {
        propertyService = null
        getPropertyMethod = null
        getValueMethod = null
    }

    companion object {
        const val TSR_SPEED_LIMIT_PROPERTY_ID = 557_847_281
        val TPMS_PRESSURE_PROPERTY_IDS =
                intArrayOf(559_948_327, 559_948_328, 559_948_329, 559_948_330)
        val TPMS_TEMPERATURE_PROPERTY_IDS =
                intArrayOf(557_851_183, 557_851_184, 557_851_185, 557_851_186)

        private const val RETRY_INTERVAL_MS = 10_000L

        /**
         * Preserves the raw TSR contract used by the Sport themes: values 1..40 are encoded
         * 5 km/h steps, while values above 40 are already expressed in km/h.
         */
        fun normalizeSpeedLimit(rawValue: String?): String? {
            val value = rawValue?.trim()?.toIntOrNull() ?: return null
            return value.takeIf { it in 1..200 }?.toString()
        }
    }
}
