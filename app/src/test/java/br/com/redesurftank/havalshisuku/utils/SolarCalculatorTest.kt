package br.com.redesurftank.havalshisuku.utils

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarCalculatorTest {

    private fun utcMillis(y: Int, mo: Int, d: Int, h: Int = 12): Long =
        LocalDate.of(y, mo, d).atTime(h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun hourUtc(ms: Long): Double {
        val z = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), ZoneOffset.UTC)
        return z.hour + z.minute / 60.0
    }

    @Test
    fun `equador no equinocio nasce ~6h e poe ~18h UTC`() {
        // Equador (0,0) no equinócio de março: dia ~12h, nascer ~06:00, pôr ~18:00 UTC.
        val t = SolarCalculator.calculate(0.0, 0.0, utcMillis(2026, 3, 20))!!
        assertTrue(t.sunriseUtcMs < t.sunsetUtcMs)
        val rise = hourUtc(t.sunriseUtcMs)
        val set = hourUtc(t.sunsetUtcMs)
        assertTrue("nascer=$rise", rise in 5.5..6.5)
        assertTrue("por=$set", set in 17.5..18.5)
        val dayLen = (t.sunsetUtcMs - t.sunriseUtcMs) / 3_600_000.0
        assertTrue("dia=$dayLen", dayLen in 11.5..12.5)
    }

    @Test
    fun `goiania em julho tem dia de inverno plausivel`() {
        // Goiânia (-16.62, -49.23), UTC-3, inverno do sul: dia ~11h, nascer ~06:5x, pôr ~18:0x local.
        val t = SolarCalculator.calculate(-16.622428, -49.233194, utcMillis(2026, 7, 21))!!
        assertTrue(t.sunriseUtcMs < t.sunsetUtcMs)
        val riseLocal = hourUtc(t.sunriseUtcMs) - 3 // BRT
        val setLocal = hourUtc(t.sunsetUtcMs) - 3
        assertTrue("nascer local=$riseLocal", riseLocal in 6.0..7.5)
        assertTrue("por local=$setLocal", setLocal in 17.0..18.75)
        val dayLen = (t.sunsetUtcMs - t.sunriseUtcMs) / 3_600_000.0
        assertTrue("dia=$dayLen", dayLen in 10.0..12.5)
    }

    @Test
    fun `goiania em janeiro tem dia mais longo que em julho`() {
        val jul = SolarCalculator.calculate(-16.62, -49.23, utcMillis(2026, 7, 21))!!
        val jan = SolarCalculator.calculate(-16.62, -49.23, utcMillis(2026, 1, 21))!!
        val julLen = jul.sunsetUtcMs - jul.sunriseUtcMs
        val janLen = jan.sunsetUtcMs - jan.sunriseUtcMs
        assertTrue("verão do sul deve ter dia mais longo", janLen > julLen)
    }

    @Test
    fun `noite polar retorna null`() {
        // Ártico (lat 80) no solstício de dezembro: noite polar -> sol não nasce.
        assertNull(SolarCalculator.calculate(80.0, 0.0, utcMillis(2026, 12, 21)))
        // E verifica que em latitude normal NÃO é null.
        assertNotNull(SolarCalculator.calculate(-16.62, -49.23, utcMillis(2026, 12, 21)))
    }
}
