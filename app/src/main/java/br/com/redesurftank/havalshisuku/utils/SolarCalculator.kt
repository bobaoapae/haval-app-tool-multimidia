package br.com.redesurftank.havalshisuku.utils

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Nascer e pôr do sol a partir de latitude/longitude/data — algoritmo NOAA (equação do nascer do
 * sol), tudo em matemática local (SEM internet). Puro/estático pra ser testável na JVM.
 *
 * Referência: https://en.wikipedia.org/wiki/Sunrise_equation
 * Longitude no padrão LESTE-positivo (ex.: Goiânia = -49.23). Retorna epoch millis UTC do nascer e
 * do pôr do sol do dia que contém `dateMillis`, ou null em dia/noite polar (sol não nasce/põe).
 */
object SolarCalculator {
    private const val DEG = Math.PI / 180.0
    private const val JD_UNIX_EPOCH = 2440587.5 // Julian Date de 1970-01-01T00:00Z
    private const val MS_PER_DAY = 86_400_000.0
    private const val OBLIQUITY = 23.44 // obliquidade da eclíptica (graus)
    // Ângulo do centro do sol no horizonte: -0.833° (raio do disco + refração atmosférica).
    private const val SUN_ALTITUDE = -0.833

    data class SunTimes(val sunriseUtcMs: Long, val sunsetUtcMs: Long)

    fun calculate(latDeg: Double, lonEastDeg: Double, dateMillis: Long): SunTimes? {
        // Número de dias desde 2000-01-01 (para o instante dado), arredondado ao dia.
        val jd = dateMillis / MS_PER_DAY + JD_UNIX_EPOCH
        val n = Math.round(jd - 2451545.0 + 0.0008).toDouble()

        // Meio-dia solar médio. Longitude LESTE-positiva entra direto aqui: n - lonLeste/360 posiciona
        // o trânsito no UTC certo (ex.: Goiânia lon=-49.23 -> trânsito ~15:17 UTC = 12:17 local solar).
        val jStar = n - lonEastDeg / 360.0
        val mDeg = (357.5291 + 0.98560028 * jStar) % 360.0 // anomalia média do sol
        val m = mDeg * DEG
        // Equação do centro (graus)
        val cDeg = 1.9148 * sin(m) + 0.0200 * sin(2 * m) + 0.0003 * sin(3 * m)
        val lambdaDeg = (mDeg + cDeg + 180.0 + 102.9372) % 360.0 // longitude eclíptica
        val lambda = lambdaDeg * DEG
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(m) - 0.0069 * sin(2 * lambda) // trânsito solar

        val declination = asin(sin(lambda) * sin(OBLIQUITY * DEG)) // declinação do sol
        val lat = latDeg * DEG
        val cosOmega =
            (sin(SUN_ALTITUDE * DEG) - sin(lat) * sin(declination)) / (cos(lat) * cos(declination))
        if (cosOmega < -1.0 || cosOmega > 1.0) return null // sol da meia-noite / noite polar
        val omega = acos(cosOmega) / DEG // ângulo horário (graus)

        val jRise = jTransit - omega / 360.0
        val jSet = jTransit + omega / 360.0
        return SunTimes(julianToMillis(jRise), julianToMillis(jSet))
    }

    private fun julianToMillis(jd: Double): Long =
        Math.round((jd - JD_UNIX_EPOCH) * MS_PER_DAY)
}
