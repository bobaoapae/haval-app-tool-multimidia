package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.PowerFlow
import br.com.redesurftank.havalshisuku.models.PowerFlow.Companion.TONE_CHARGE
import br.com.redesurftank.havalshisuku.models.PowerFlow.Companion.TONE_EV
import br.com.redesurftank.havalshisuku.models.PowerFlow.Companion.TONE_HYBRID
import br.com.redesurftank.havalshisuku.models.PowerFlow.Companion.TONE_ICE
import br.com.redesurftank.havalshisuku.models.PowerFlow.Companion.TONE_IDLE
import br.com.redesurftank.havalshisuku.models.PowerFlow.Companion.TONE_REGEN

/**
 * BeanEnergyAssistant energy_drive_state table plus AWD idle inference.
 *
 * ICE on the result is always [iceOn] (RPM), even when the OEM enum says
 * hybrid — that enum is a power-path mode, not "the engine is firing".
 */
object PowerFlowMapper {
    private val IDLE = PowerFlow(TONE_IDLE, false, 0, 0, "Parado")
    private val CHARGE = PowerFlow(TONE_CHARGE, false, 0, 0, "Carregando")

    private val TABLE: Map<Int, PowerFlow> = mapOf(
        0 to IDLE,
        10 to IDLE,
        11 to PowerFlow(TONE_IDLE, true, 0, 0, "Parado"),
        21 to PowerFlow(TONE_IDLE, true, 0, 0, "Marcha lenta"),
        14 to PowerFlow(TONE_EV, false, 1, 0, "Elétrico"),
        22 to PowerFlow(TONE_EV, false, 1, 0, "Elétrico"),
        6 to PowerFlow(TONE_REGEN, false, -1, 0, "Regen"),
        13 to PowerFlow(TONE_REGEN, true, -1, 0, "Regen"),
        15 to PowerFlow(TONE_HYBRID, true, 1, 0, "Híbrido"),
        16 to PowerFlow(TONE_HYBRID, true, 1, 0, "Híbrido"),
        17 to PowerFlow(TONE_HYBRID, true, 1, 0, "Híbrido"),
        3 to PowerFlow(TONE_ICE, true, 0, 0, "Paralelo"),
        20 to PowerFlow(TONE_HYBRID, true, 1, 0, "Combinado"),
        1 to PowerFlow(TONE_CHARGE, true, 1, 0, "Carga"),
        12 to PowerFlow(TONE_CHARGE, true, 0, 0, "Carga lenta"),
        18 to PowerFlow(TONE_CHARGE, false, 0, 0, "Aquecendo"),
        23 to CHARGE,
        45 to CHARGE,
        46 to CHARGE,
        30 to PowerFlow(TONE_CHARGE, true, 1, 1, "Carga P2+P4"),
        31 to PowerFlow(TONE_HYBRID, true, 1, 1, "P4 + carga P2"),
        32 to PowerFlow(TONE_CHARGE, true, 1, 0, "Carga P2"),
        33 to PowerFlow(TONE_CHARGE, true, 0, 1, "Carga P4"),
        34 to PowerFlow(TONE_HYBRID, true, 0, 1, "Motor + P4"),
        35 to PowerFlow(TONE_HYBRID, true, 1, 1, "Motor + P2+P4"),
        36 to PowerFlow(TONE_HYBRID, true, 1, 0, "Motor + P2"),
        37 to PowerFlow(TONE_HYBRID, true, 1, 1, "P4, carga P2"),
        38 to PowerFlow(TONE_REGEN, false, -1, 0, "Regen dianteiro"),
        39 to PowerFlow(TONE_REGEN, false, 0, -1, "Regen traseiro"),
        40 to PowerFlow(TONE_EV, false, 1, 1, "P2 + P4"),
        41 to PowerFlow(TONE_EV, false, 0, 1, "P4"),
        42 to PowerFlow(TONE_EV, false, 1, 0, "P2"),
    )

    @JvmStatic
    fun parseDrive(raw: String?): Int {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return 0
        s.toIntOrNull()?.let { return it }
        return s.toDoubleOrNull()?.toInt() ?: 0
    }

    @JvmStatic
    fun parseNumber(raw: String?): Double {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return Double.NaN
        return s.toDoubleOrNull() ?: Double.NaN
    }

    @JvmStatic
    fun packKw(voltage: String?, current: String?): Double {
        val v = parseNumber(voltage)
        val i = parseNumber(current)
        if (!v.isFinite() || !i.isFinite()) return 0.0
        return v * i / 1000.0
    }

    @JvmStatic
    fun resolve(
        driveState: String?,
        iceOn: Boolean,
        speedKmh: Double,
        packKw: Double,
        charging: Boolean,
    ): PowerFlow {
        if (charging) return CHARGE
        val mapped = TABLE[parseDrive(driveState)] ?: IDLE
        val moving = speedKmh.isFinite() && speedKmh >= 0.5
        val flow = if (mapped.tone == TONE_IDLE && moving) {
            inferAwd(iceOn, packKw)
        } else {
            mapped
        }
        return flow.copy(iceOn = iceOn)
    }

    /**
     * Dual-motor AWD often lands on an unmapped/idle enum while both axles
     * are live. If we are moving with pack power, do not sit on Parado.
     */
    private fun inferAwd(iceOn: Boolean, kw: Double): PowerFlow {
        if (kw < -0.15) {
            return PowerFlow(TONE_REGEN, iceOn, -1, -1, "Regen")
        }
        if (kw > 0.15 && iceOn) {
            return PowerFlow(TONE_HYBRID, true, 1, 1, "Híbrido")
        }
        if (kw > 0.15) {
            return PowerFlow(TONE_EV, false, 1, 1, "P2 + P4")
        }
        if (iceOn) {
            return PowerFlow(TONE_IDLE, true, 0, 0, "Marcha lenta")
        }
        return IDLE
    }
}
