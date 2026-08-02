package br.com.redesurftank.havalshisuku.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessScheduleTest {

    private val day = 10
    private val night = 1
    private val step = 5 * 60 * 1000L          // 5 min por degrau de 1 nível
    private val gap = day - night              // 9
    private val half = gap * step / 2          // 22,5 min
    private val sunrise = 6 * 3_600_000L       // 06:00
    private val sunset = 18 * 3_600_000L       // 18:00

    private fun levelAt(ms: Long) =
        BrightnessSchedule.levelFor(ms, sunrise, sunset, day, night, step)

    @Test
    fun `total acompanha o gap`() {
        assertEquals(9L * step, BrightnessSchedule.totalMs(10, 1, step)) // 45 min
        assertEquals(6L * step, BrightnessSchedule.totalMs(8, 2, step))  // 30 min
    }

    @Test
    fun `madrugada dia e noite fora da transicao`() {
        assertEquals(night, levelAt(2 * 3_600_000L).level)
        assertFalse(levelAt(2 * 3_600_000L).inTransition)
        assertEquals(day, levelAt(12 * 3_600_000L).level)
        assertEquals(night, levelAt(22 * 3_600_000L).level)
    }

    @Test
    fun `amanhecer sobe 1 nivel a cada passo`() {
        val start = sunrise - half
        for (s in 0..8) {
            val r = levelAt(start + s * step)
            assertEquals("passo $s", night + s, r.level) // 1,2,3,...,9
            assertTrue(r.inTransition)
        }
        assertEquals(day, levelAt(sunrise + half).level) // fim exato: dia (10)
    }

    @Test
    fun `entardecer desce 1 nivel a cada passo`() {
        val start = sunset - half
        for (s in 0..8) {
            val r = levelAt(start + s * step)
            assertEquals("passo $s", day - s, r.level)   // 10,9,8,...,2
            assertTrue(r.inTransition)
        }
        assertEquals(night, levelAt(sunset + half).level) // fim exato: noite (1)
    }

    @Test
    fun `no sol exato fica proximo do meio`() {
        // elapsed = half = 22,5 min -> floor(22,5/5)=4 degraus
        assertEquals(night + 4, levelAt(sunrise).level) // 5
        assertEquals(day - 4, levelAt(sunset).level)    // 6
    }

    @Test
    fun `amanhecer nunca cai e entardecer nunca sobe`() {
        var prev = 0
        var t = sunrise - half
        while (t <= sunrise + half) { val l = levelAt(t).level; assertTrue(l >= prev); prev = l; t += 60_000L }
        assertEquals(day, prev)
        prev = 99; t = sunset - half
        while (t <= sunset + half) { val l = levelAt(t).level; assertTrue(l <= prev); prev = l; t += 60_000L }
        assertEquals(night, prev)
    }

    @Test
    fun `passo nunca pula mais de 1 nivel`() {
        var t = sunrise - half; var prev = levelAt(t).level
        while (t <= sunrise + half) {
            val l = levelAt(t).level
            assertTrue("pulo de ${l - prev} em t=$t", kotlin.math.abs(l - prev) <= 1)
            prev = l; t += 60_000L
        }
    }

    @Test
    fun `gap zero = switch seco pela posicao do sol`() {
        val r1 = BrightnessSchedule.levelFor(12 * 3_600_000L, sunrise, sunset, 5, 5, step)
        assertEquals(5, r1.level); assertFalse(r1.inTransition)
        val r2 = BrightnessSchedule.levelFor(2 * 3_600_000L, sunrise, sunset, 5, 5, step)
        assertEquals(5, r2.level)
    }
}
