package br.com.redesurftank.havalshisuku.radar

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * One fixed speed camera. [bearing] is the direction of travel it enforces (degrees, 0 = north,
 * clockwise); null when unknown or when it enforces both ways, so only the car's heading decides.
 * [limitKmh] is null when the source has no usable limit.
 */
data class RadarCamera(
    val lat: Double,
    val lon: Double,
    val limitKmh: Int?,
    val bearing: Float?,
    val source: Source
) {
    enum class Source(val code: String) {
        /** OpenStreetMap `highway=speed_camera` (ODbL). */
        OSM("o"),
        /** ANTT "Dados dos Radares", federal concession highways (CC-BY). */
        ANTT("a");

        companion object {
            fun fromCode(code: String): Source? = entries.firstOrNull { it.code == code }
        }
    }
}

/** Flat-earth maths: good to well under 1 % at the few-km range alerts work in. */
object RadarGeo {
    private const val EARTH_M = 6_371_000.0
    /** Metres per degree of latitude (and of longitude at the equator). */
    const val METERS_PER_DEG = 111_320.0

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
        val y = Math.toRadians(lat2 - lat1)
        return EARTH_M * hypot(x, y)
    }

    /** Initial bearing from point 1 to point 2, degrees 0..360 (0 = north, clockwise). */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
        val y = Math.toRadians(lat2 - lat1)
        return normalize(Math.toDegrees(atan2(x, y)).toFloat())
    }

    /** Smallest angle between two bearings, 0..180. */
    fun angleDiff(a: Float, b: Float): Float {
        val d = abs(normalize(a) - normalize(b))
        return if (d > 180f) 360f - d else d
    }

    fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    /** OSM `direction`: degrees or a compass point; null for anything else (both, forward, ...). */
    fun parseCompass(raw: String?): Float? {
        val text = raw?.trim()?.uppercase() ?: return null
        text.toFloatOrNull()?.let { return if (it.isFinite()) normalize(it) else null }
        val points = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val i = points.indexOf(text)
        return if (i >= 0) i * 22.5f else null
    }

    /** "60", "60 km/h", "60;80" -> 60. Plausible limits only (10..150), else null. */
    fun parseLimit(raw: String?): Int? {
        val digits = raw?.trim()?.takeWhile { it.isDigit() } ?: return null
        val n = digits.toIntOrNull() ?: return null
        return n.takeIf { it in 10..150 }
    }

    fun roundBearing(deg: Float?): Int? = deg?.let { normalize(it).roundToInt() % 360 }
}
