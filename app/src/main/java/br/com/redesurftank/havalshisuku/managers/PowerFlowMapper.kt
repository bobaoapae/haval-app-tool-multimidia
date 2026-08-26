package br.com.redesurftank.havalshisuku.managers

import br.com.redesurftank.havalshisuku.models.PowerFlow
import br.com.redesurftank.havalshisuku.models.PowerFlow.Companion.STATE_CHARGE
import br.com.redesurftank.havalshisuku.models.PowerFlow.Companion.STATE_EV
import br.com.redesurftank.havalshisuku.models.PowerFlow.Companion.STATE_HYBRID
import br.com.redesurftank.havalshisuku.models.PowerFlow.Companion.STATE_ICE
import br.com.redesurftank.havalshisuku.models.PowerFlow.Companion.STATE_IDLE
import br.com.redesurftank.havalshisuku.models.PowerFlow.Companion.STATE_REGEN

/**
 * BeanEnergyAssistant energy_drive_state table plus AWD idle inference.
 *
 * ICE on the result is always [iceOn] (RPM), even when the OEM enum says
 * hybrid — that enum is a power-path mode, not "the engine is firing".
 */
object PowerFlowMapper {
    private val IDLE = PowerFlow(STATE_IDLE, false, 0, 0)
    private val CHARGE = PowerFlow(STATE_CHARGE, false, 0, 0)

    private val TABLE: Map<Int, PowerFlow> = mapOf(
        0 to IDLE,
        10 to IDLE,
        11 to IDLE,
        21 to IDLE,
        14 to PowerFlow(STATE_EV, false, 1, 0),
        22 to PowerFlow(STATE_EV, false, 1, 0),
        6 to PowerFlow(STATE_REGEN, false, -1, 0),
        13 to PowerFlow(STATE_REGEN, true, -1, 0),
        15 to PowerFlow(STATE_HYBRID, true, 1, 0),
        16 to PowerFlow(STATE_HYBRID, true, 1, 0),
        17 to PowerFlow(STATE_HYBRID, true, 1, 0),
        3 to PowerFlow(STATE_ICE, true, 0, 0),
        20 to PowerFlow(STATE_HYBRID, true, 1, 0),
        1 to PowerFlow(STATE_CHARGE, true, 1, 0),
        12 to PowerFlow(STATE_CHARGE, true, 0, 0),
        18 to PowerFlow(STATE_CHARGE, false, 0, 0),
        23 to CHARGE,
        45 to CHARGE,
        46 to CHARGE,
        30 to PowerFlow(STATE_CHARGE, true, 1, 1),
        31 to PowerFlow(STATE_HYBRID, true, 1, 1),
        32 to PowerFlow(STATE_CHARGE, true, 1, 0),
        33 to PowerFlow(STATE_CHARGE, true, 0, 1),
        34 to PowerFlow(STATE_HYBRID, true, 0, 1),
        35 to PowerFlow(STATE_HYBRID, true, 1, 1),
        36 to PowerFlow(STATE_HYBRID, true, 1, 0),
        37 to PowerFlow(STATE_HYBRID, true, 1, 1),
        38 to PowerFlow(STATE_REGEN, false, -1, 0),
        39 to PowerFlow(STATE_REGEN, false, 0, -1),
        40 to PowerFlow(STATE_EV, false, 1, 1),
        41 to PowerFlow(STATE_EV, false, 0, 1),
        42 to PowerFlow(STATE_EV, false, 1, 0),
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
        val flow = if (mapped.state == STATE_IDLE && moving) {
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
            return PowerFlow(STATE_REGEN, iceOn, -1, -1)
        }
        if (kw > 0.15 && iceOn) {
            return PowerFlow(STATE_HYBRID, true, 1, 1)
        }
        if (kw > 0.15) {
            return PowerFlow(STATE_EV, false, 1, 1)
        }
        if (iceOn) {
            return PowerFlow(STATE_IDLE, true, 0, 0)
        }
        return IDLE
    }
}
