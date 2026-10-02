package br.com.redesurftank.havalshisuku.diagnostics

import java.security.MessageDigest

/**
 * Pure helpers for the anonymous fleet ping.
 *
 * `vehicle_uuid` is a SHA-256 of the **device id**, not of the VIN. That is deliberate: a VIN is
 * highly structured and nearly fully determined by fields this event already carries, so a
 * client-side hash of it is brute-forceable in seconds. Hashing a random per-device value instead
 * keeps the id stable across reinstalls while leaving nothing to enumerate.
 */
object AnonymousTelemetryPayload {
    const val EVENT_NAME = "impulse_fleet_ping"
    const val ODOMETER_BUCKET_KM = 1_000
    const val DEBOUNCE_MS = 5L * 60L * 1000L

    /** Salted so a ROM that hands every app the same device id still yields an app-scoped value. */
    fun vehicleUuid(deviceId: String, salt: String): String {
        val normalized = deviceId.trim().uppercase()
        val digest =
                MessageDigest.getInstance("SHA-256")
                        .digest("$normalized|$salt".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun odometerBucketKm(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace(',', '.')
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value < 0 || value.isNaN() || value.isInfinite()) return null
        return (((value.toLong()) / ODOMETER_BUCKET_KM) * ODOMETER_BUCKET_KM).toInt()
    }

    fun shouldDebounce(lastSentAtMs: Long, nowMs: Long, windowMs: Long = DEBOUNCE_MS): Boolean {
        if (lastSentAtMs <= 0L) return false
        return nowMs - lastSentAtMs < windowMs
    }

    fun themeChanged(currentTheme: String, lastReportedTheme: String?): Boolean {
        if (lastReportedTheme.isNullOrBlank()) return false
        return currentTheme != lastReportedTheme
    }

    fun buildProperties(
            vehicleUuid: String,
            vehicleModel1: String?,
            vehicleModel2: String?,
            configureCode: String?,
            trimLevel: String?,
            carMode1: String?,
            engineType: String?,
            projectName: String?,
            odometerKmBucket: Int?,
            theme: String,
            themeChanged: Boolean,
            powerOnCount: Long,
            virtualClusterEnabled: Boolean,
            settings: Map<String, Boolean>,
            appVersion: String,
            versionCode: Int
    ): Map<String, Any?> {
        val props = linkedMapOf<String, Any?>(
                "\$process_person_profile" to false,
                "vehicle_uuid" to vehicleUuid,
                "theme" to theme,
                "theme_changed" to themeChanged,
                "power_on_count" to powerOnCount,
                "virtual_cluster_enabled" to virtualClusterEnabled,
                "app_version" to appVersion,
                "version_code" to versionCode
        )
        putIfNotBlank(props, "vehicle_model1", vehicleModel1)
        putIfNotBlank(props, "vehicle_model2", vehicleModel2)
        putIfNotBlank(props, "configure_code", configureCode)
        putIfNotBlank(props, "trim_level", trimLevel)
        putIfNotBlank(props, "car_mode1", carMode1)
        putIfNotBlank(props, "engine_type", engineType)
        putIfNotBlank(props, "project_name", projectName)
        if (odometerKmBucket != null) {
            props["odometer_km_bucket"] = odometerKmBucket
        }
        settings.forEach { (key, value) -> props[key] = value }
        return props
    }

    private fun putIfNotBlank(target: MutableMap<String, Any?>, key: String, value: String?) {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isNotEmpty()) target[key] = trimmed
    }
}
