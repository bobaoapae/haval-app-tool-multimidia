package br.com.redesurftank.havalshisuku.utils

import kotlin.math.abs

/**
 * Rampa de brilho por nascer/pôr do sol (pura/testável). Transição CENTRADA no evento, dando
 * UM DEGRAU DE 1 NÍVEL a cada `stepMs` (padrão 5 min). O total acompanha o gap:
 * duração = |dia − noite| × stepMs (ex.: dia 10 / noite 1 = 9 passos = 45 min, 22,5 antes e 22,5
 * depois do sol). Fora das rampas, mantém dia ou noite. O agendamento do próximo tick fica no
 * manager (durante a rampa, re-tica a cada `stepMs`).
 */
object BrightnessSchedule {
    const val DEFAULT_STEP_MIN = 5
    const val MIN_STEP_MIN = 1
    const val MAX_STEP_MIN = 30

    data class Result(val level: Int, val inTransition: Boolean)

    /** Duração total (ms) da transição pra este gap de níveis. */
    fun totalMs(dayLevel: Int, nightLevel: Int, stepMs: Long): Long =
        abs(dayLevel - nightLevel).toLong() * stepMs

    fun levelFor(
        nowMs: Long,
        sunriseMs: Long,
        sunsetMs: Long,
        dayLevel: Int,
        nightLevel: Int,
        stepMs: Long
    ): Result {
        val gap = abs(dayLevel - nightLevel)
        // Sem gap ou passo inválido: switch seco pela posição do sol (dia entre nascer e pôr).
        if (gap == 0 || stepMs <= 0L) {
            val isDay = nowMs in sunriseMs until sunsetMs
            return Result(if (isDay) dayLevel else nightLevel, false)
        }
        val half = gap.toLong() * stepMs / 2
        return when {
            nowMs < sunriseMs - half -> Result(nightLevel, false)            // madrugada
            nowMs < sunriseMs + half ->                                      // amanhecer: noite -> dia
                Result(stepLevel(nightLevel, dayLevel, nowMs - (sunriseMs - half), stepMs, gap), true)
            nowMs < sunsetMs - half -> Result(dayLevel, false)               // dia pleno
            nowMs < sunsetMs + half ->                                       // entardecer: dia -> noite
                Result(stepLevel(dayLevel, nightLevel, nowMs - (sunsetMs - half), stepMs, gap), true)
            else -> Result(nightLevel, false)                               // noite (após o pôr)
        }
    }

    // Anda `elapsed/stepMs` degraus de 1 nível de `from` rumo a `to` (limitado ao gap).
    private fun stepLevel(from: Int, to: Int, elapsedMs: Long, stepMs: Long, gap: Int): Int {
        val steps = (elapsedMs / stepMs).coerceIn(0L, gap.toLong()).toInt()
        val dir = if (to >= from) 1 else -1
        return from + dir * steps
    }
}
