package br.com.redesurftank.havalshisuku.hud

import kotlin.math.min

/**
 * Progress round a roundabout from Android Auto's own distance (owner, 2026-10-18): on the
 * roundabout AA counts down the metres to the exit. So when the distance to a roundabout step
 * drops to ~0 at the entry and then jumps back up, the car is on the ring and that new distance
 * is the whole way round; progress = 1 - now / at entry. Without such a jump (A confirmar on
 * this car) the controller keeps its speed-based estimate.
 */
class RoundaboutTracker {
    private var key: String? = null
    private var minM = Float.MAX_VALUE

    /** AA's distance to the exit when the car entered the ring; null until then. */
    var startM: Float? = null
        private set

    val entered: Boolean get() = startM != null

    /**
     * Feed every distance AA reports. [step] identifies the roundabout step (null for any
     * other maneuver), [meters] AA's distance (null = unknown or stale). True when the entry
     * is detected on this call.
     */
    fun onUpdate(step: String?, meters: Float?): Boolean {
        if (step != key) {
            key = step
            minM = Float.MAX_VALUE
            startM = null
        }
        if (step == null || meters == null) return false
        if (startM == null && minM <= ENTRY_M && meters >= minM + JUMP_M) {
            startM = meters
            return true
        }
        minM = min(minM, meters)
        return false
    }

    /** 0 at the entry .. 1 at the exit, from AA's distance [currentM] now. */
    fun progress(currentM: Float): Float {
        val start = startM ?: return 0f
        return if (start <= 0f) 1f else (1f - currentM / start).coerceIn(0f, 1f)
    }

    companion object {
        /** The distance must have come this close (the entry) before a jump counts. */
        const val ENTRY_M = 20f
        /** ...and jump back up by at least this. */
        const val JUMP_M = 10f
    }
}
